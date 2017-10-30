//**************************************************************************
// RISCV Processor Tile
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2013 Jun 28
//

package zynq
{

import chisel3._
import uncore.tilelink2._
import diplomacy._
import util._
import uncore.axi4._
import config._
import Common._   
import RV32_3stage._

class TLToDMIBundle(val outer: TLToDMI)(implicit p: Parameters) extends Bundle(){
   val dmi = new DMIIO()
   val tl_in = outer.slaveDebug.bundleIn
}

class TLToDMIModule(val outer: TLToDMI)(implicit p: Parameters) extends LazyModuleImp(outer){
   val io = new TLToDMIBundle(outer)
   val edge_in = outer.slaveDebug.edgesIn.head
   val tl_in = io.tl_in.head
   val areq = RegEnable(tl_in.a.bits, tl_in.a.fire())
   val temp3 = Wire(init = false.B)
   io.dmi.req.valid := tl_in.a.valid
   io.dmi.req.bits.data := tl_in.a.bits.data
   io.dmi.req.bits.addr := (tl_in.a.bits.address & "h1ff".U) >> 2.U
   tl_in.a.ready := io.dmi.req.ready 
   tl_in.d.valid := io.dmi.resp.valid 
   io.dmi.resp.ready := tl_in.d.ready
   io.dmi.req.bits.op := Mux(tl_in.a.bits.opcode === 4.U, DMConsts.dmi_OP_READ, DMConsts.dmi_OP_WRITE)
   temp3 := tl_in.a.valid && io.dmi.resp.valid
   tl_in.d.bits := Mux(io.dmi.req.valid && io.dmi.resp.valid ,edge_in.AccessAck(tl_in.a.bits, 0.U),edge_in.AccessAck(areq, 0.U))
   tl_in.d.bits.data := io.dmi.resp.bits.data
   tl_in.d.bits.opcode := Mux(((areq.opcode === 4.U) && !temp3) || tl_in.a.bits.opcode === 4.U , TLMessages.AccessAckData, TLMessages.AccessAck)

   // Tie off unused channels
   tl_in.b.valid := false.B
   tl_in.c.ready := true.B
   tl_in.e.ready := true.B
}


class TLToDMI(implicit p: Parameters) extends LazyModule{
  lazy val module = new TLToDMIModule(this)
  val config = p(DebugAddrSlave) //temporary
  val slaveDebug = TLManagerNode(Seq(TLManagerPortParameters(
      Seq(TLManagerParameters(
        address         = Seq(AddressSet(config.base, config.size-1)),
        regionType      = RegionType.UNCACHED,
        executable      = false,
        supportsPutFull = TransferSizes(1, 4),
        supportsPutPartial = TransferSizes(1, 4),
        supportsGet     = TransferSizes(1, 4),
        fifoId          = Some(0))), // requests handled in FIFO order
      beatBytes = 4,
      minLatency = 1)))
}

class SodorTileBundle(outer: SodorTile)(implicit p: Parameters) extends Bundle {
   val mem_axi4 = outer.mem_axi4.bundleOut
   val ps_slave = outer.ps_slave.bundleIn
}

class SodorTileModule(outer: SodorTile)(implicit p: Parameters) extends LazyModuleImp(outer){
   val io = new SodorTileBundle(outer)
   val core   = Module(new Core())
   val memory = outer.memory.module 
   val tldmi = outer.tldmi.module
   val debug = Module(new DebugModule())
   core.reset := debug.io.resetcore | reset.toBool

   core.io.dmem <> memory.io.core_ports(0)
   core.io.imem <> memory.io.core_ports(1)
   debug.io.debugmem <> memory.io.debug_port

   // DTM memory access
   debug.io.ddpath <> core.io.ddpath
   debug.io.dcpath <> core.io.dcpath 
   debug.io.dmi <> tldmi.io.dmi
}


class SodorTile(implicit p: Parameters) extends LazyModule
{
   val memory = LazyModule(new MemAccessToTL(num_core_ports=2))
   val tldmi = LazyModule(new TLToDMI())
   lazy val module = Module(new SodorTileModule(this))
   private val device = new MemoryDevice
   val config = p(ExtMem)
   val mmio = p(MMIO)
   val mem_axi4 = AXI4BlindOutputNode(Seq(  
    AXI4SlavePortParameters(
      slaves = Seq(AXI4SlaveParameters(
              address       = Seq(AddressSet(config.base, config.size-1),AddressSet(mmio.base, mmio.size-1)),
              resources     = device.reg,
              regionType    = RegionType.UNCACHED,   // cacheable
              executable    = false,
              supportsWrite = TransferSizes(1, 4), // The slave supports 1-256 byte transfers
              supportsRead  = TransferSizes(1, 4),
              interleavedId = Some(0))),             // slave does not interleave read responses
            beatBytes = 4,minLatency =1)
   ))
   
   val tlxbar = LazyModule(new TLXbar)

   mem_axi4 := AXI4Buffer()(
    AXI4UserYanker(Some(4))(
    AXI4IdIndexer(4)(
    TLToAXI4(4)(tlxbar.node))))

   tlxbar.node := memory.masterDebug
   tlxbar.node := memory.masterInstr
   tlxbar.node := memory.masterData 

   val tlxbar2 = LazyModule(new TLXbar)
   val error = LazyModule(new TLError(address = Seq(AddressSet(0x3000, 0xfff))))
   val ps_slave = AXI4BlindInputNode(Seq(AXI4MasterPortParameters(
    masters = Seq(AXI4MasterParameters(name = "AXI4 periphery")))))

   error.node := tlxbar2.node
   tldmi.slaveDebug := tlxbar2.node
   tlxbar2.node :=
    TLFIFOFixer()(
    TLBuffer()(
    TLWidthWidget(4)(
    AXI4ToTL()(
    AXI4UserYanker(Some(2))(
    AXI4Fragmenter()(
    AXI4IdIndexer(3)(ps_slave)))))))
}
 
}
