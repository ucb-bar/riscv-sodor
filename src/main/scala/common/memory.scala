//**************************************************************************
// Scratchpad Memory (asynchronous)
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2013 Jun 12
//
// Provides a variable number of ports to the core, and one port to the HTIF
// (host-target interface).
//
// Assumes that if the port is ready, it will be performed immediately.
// For now, don't detect write collisions.
//
// Optionally uses synchronous read (default is async). For example, a 1-stage
// processor can only ever work using asynchronous memory!

package sodor.common

import chisel3._
import chisel3.util._
import chisel3.experimental._

import Constants._
import sodor.common.Util._

trait MemoryOpConstants
{
   val MT_X  = 0.asUInt(3.W)
   val MT_B  = 1.asUInt(3.W)
   val MT_H  = 2.asUInt(3.W)
   val MT_W  = 3.asUInt(3.W)
   val MT_D  = 4.asUInt(3.W)
   val MT_BU = 5.asUInt(3.W)
   val MT_HU = 6.asUInt(3.W)
   val MT_WU = 7.asUInt(3.W)

   val M_X   = "b0".asUInt(1.W)
   val M_XRD = "b0".asUInt(1.W) // int load
   val M_XWR = "b1".asUInt(1.W) // int store

   val DPORT = 0
   val IPORT = 1
}

// from the pov of the datapath
class MemPortIo(data_width: Int)(implicit val conf: SodorCoreParams) extends Bundle
{
   val req    = new DecoupledIO(new MemReq(data_width))
   val resp   = Flipped(new ValidIO(new MemResp(data_width)))
}

class MemReq(data_width: Int)(implicit val conf: SodorCoreParams) extends Bundle
{
   val addr = Output(UInt(conf.xprlen.W))
   val data = Output(UInt(data_width.W))
   val fcn  = Output(UInt(M_X.getWidth.W))  // memory function code
   val typ  = Output(UInt(MT_X.getWidth.W)) // memory type
   // To convert MemPortIO type to sign and size in TileLink format: subtract 1 from type, then take inversed MSB as signedness
  // and the remaining two bits as TileLink size
  def getTLSize = (typ - 1.U)(1, 0)
  def getTLSigned = ~(typ - 1.U)(2)
  def setType(tlSigned: Bool, tlSize: UInt) = { typ := Cat(~tlSigned, tlSize + 1.U) }
}

class MemResp(data_width: Int) extends Bundle
{
   val data = Output(UInt(data_width.W))
}

// Note: All `size` field in this class are base 2 logarithm 
class MemoryModule(numBytes: Int, useAsync: Boolean) {
   val addrWidth = log2Ceil(numBytes)
   val mem = if (useAsync) Mem(numBytes / 4, Vec(4, UInt(8.W))) else SyncReadMem(numBytes / 4, Vec(4, UInt(8.W)))

   // Convert size exponent to actual number of bytes - 1
   private def sizeToBytes(size: UInt) = MuxLookup(size, 3.U, List(0.U -> 0.U, 1.U -> 1.U, 2.U -> 3.U))

   private def getMask(bytes: UInt, storeOffset: UInt = 0.U) = {
      val mask = ("b00011111".U(8.W) << bytes).apply(7, 4)
      val maskWithOffset = (mask << storeOffset).apply(3, 0)
      maskWithOffset.asBools.reverse
   }

   private def splitWord(data: UInt) = VecInit(((data(31, 0).asBools.reverse grouped 8) map (bools => Cat(bools))).toSeq)

   // Read function
   def read(addr: UInt, size: UInt, signed: Bool) = {
      // Create a module to show signal inside
      class MemReader extends Module {
         val io = IO(new Bundle {
            val addr = Input(UInt(addrWidth.W))
            val size = Input(UInt(2.W))
            val signed = Input(Bool())
            val data = Output(UInt(32.W))

            val mem_addr = Output(UInt((addrWidth - 2).W))
            val mem_data = Input(Vec(4, UInt(8.W)))
         })
         // Sync argument if needed
         val s_offset = if (useAsync) io.addr(1, 0) else RegNext(io.addr(1, 0))
         val s_size = if (useAsync) io.size else RegNext(io.size)
         val s_signed = if (useAsync) io.signed else RegNext(io.signed)

         // Read data from the banks and align
         io.mem_addr := io.addr(addrWidth - 1, 2)
         val readVec = io.mem_data
         val shiftedVec = splitWord(Cat(readVec) >> (s_offset << 3))

         // Mask data according to the size
         val bytes = sizeToBytes(s_size)
         val sign = shiftedVec(3.U - bytes).apply(7)
         val masks = getMask(bytes)
         val maskedVec = (shiftedVec zip masks) map ({ case (byte, mask) => 
            Mux(sign && s_signed, byte | ~Fill(8, mask), byte & Fill(8, mask))
         })

         io.data := Cat(maskedVec)
      }

      val module = Module(new MemReader)
      module.io.addr := addr
      module.io.size := size
      module.io.signed := signed
      module.io.mem_data := mem.read(module.io.mem_addr)

      module.io.data
   }
   def apply(addr: UInt, size: UInt, signed: Bool) = read(addr, size, signed)

   // Write function
   def write(addr: UInt, data: UInt, size: UInt, en: Bool) = {
      // Create a module to show signal inside
      class MemWriter extends Module {
         val io = IO(new Bundle {
            val addr = Input(UInt(addrWidth.W))
            val data = Input(UInt(32.W))
            val size = Input(UInt(2.W))
            val en = Input(Bool())

            val mem_addr = Output(UInt((addrWidth - 2).W))
            val mem_data = Output(Vec(4, UInt(8.W)))
            val mem_masks = Output(Vec(4, Bool()))
         })

         // Align data and mask
         val offset = io.addr(1, 0)
         val shiftedVec = splitWord(io.data << (offset << 3))
         val masks = getMask(sizeToBytes(io.size), offset)

         // Write
         io.mem_addr := io.addr(addrWidth - 1, 2)
         io.mem_data := shiftedVec
         io.mem_masks := VecInit(masks map (mask => mask && io.en))
      }

      val module = Module(new MemWriter)
      module.io.addr := addr
      module.io.data := data
      module.io.size := size
      module.io.en := en

      when (en) {
         mem.write(module.io.mem_addr, module.io.mem_data, module.io.mem_masks)
      }
   }
}

// NOTE: the default is enormous (and may crash your computer), but is bound by
// what the fesvr expects the smallest memory size to be.  A proper fix would
// be to modify the fesvr to expect smaller sizes.
//for 1,2 and 5 stage need for combinational reads
class ScratchPadMemoryBase(num_core_ports: Int, num_bytes: Int = (1 << 21), useAsync: Boolean = true)(implicit val conf: SodorCoreParams) extends Module
{
   val io = IO(new Bundle
   {
      val core_ports = Vec(num_core_ports, Flipped(new MemPortIo(data_width = conf.xprlen)) )
      val debug_port = Flipped(new MemPortIo(data_width = 32))
   })
   val num_bytes_per_line = 8
   val num_lines = num_bytes / num_bytes_per_line
   println("\n    Sodor Tile: creating Asynchronous Scratchpad Memory of size " + num_lines*num_bytes_per_line/1024 + " kB\n")
   val async_data = new MemoryModule(num_bytes, useAsync)
   for (i <- 0 until num_core_ports)
   {
      io.core_ports(i).resp.valid := (if (useAsync) io.core_ports(i).req.valid else RegNext(io.core_ports(i).req.valid, false.B))
      io.core_ports(i).req.ready := true.B // for now, no back pressure
   }

   /////////// DPORT
   val req_addri = io.core_ports(DPORT).req.bits.addr

   val dport_req = io.core_ports(DPORT).req.bits
   val dport_wen = io.core_ports(DPORT).req.valid && dport_req.fcn === M_XWR
   io.core_ports(DPORT).resp.bits.data := async_data.read(dport_req.addr, dport_req.getTLSize, dport_req.getTLSigned)
   async_data.write(dport_req.addr, dport_req.data, dport_req.getTLSize, dport_wen)
   /////////////////

   ///////////// IPORT
   if (num_core_ports == 2){
      val iport_req = io.core_ports(IPORT).req.bits 
      io.core_ports(IPORT).resp.bits.data := async_data.read(iport_req.addr, iport_req.getTLSize, iport_req.getTLSigned)
   }
   ////////////

   // DEBUG PORT-------
   io.debug_port.req.ready := true.B // for now, no back pressure
   io.debug_port.resp.valid := (if (useAsync) io.debug_port.req.valid else RegNext(io.debug_port.req.valid, false.B))
   // asynchronous read
   val debug_port_req = io.debug_port.req.bits
   val debug_port_wen = io.debug_port.req.valid && debug_port_req.fcn === M_XWR
   io.debug_port.resp.bits.data := async_data.read(debug_port_req.addr, debug_port_req.getTLSize, debug_port_req.getTLSigned)
   async_data.write(debug_port_req.addr, debug_port_req.data, debug_port_req.getTLSize, debug_port_wen)
}

class AsyncScratchPadMemory(num_core_ports: Int, num_bytes: Int = (1 << 21))(implicit conf: SodorCoreParams) 
   extends ScratchPadMemoryBase(num_core_ports, num_bytes, true)(conf)

class SyncScratchPadMemory(num_core_ports: Int, num_bytes: Int = (1 << 21))(implicit conf: SodorCoreParams) 
   extends ScratchPadMemoryBase(num_core_ports, num_bytes, false)(conf)
