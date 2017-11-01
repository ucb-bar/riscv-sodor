package zynqsimtop

import chisel3._
import chisel3.iotesters._
import zynq._
import Common._
import diplomacy._
import config.{Parameters, Field}
import RV32_3stage.Constants._

class DMItoAXI4(top: Top)(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val ps_axi_slave = top.tile.io.ps_slave.cloneType
    val dmi = Flipped(new DMIIO())
    val success = Output(Bool())
  })
  val debugtlbase = p(DebugAddrSlave).base.U
  when(io.dmi.req.bits.op === DMConsts.dmi_OP_WRITE){
    io.ps_axi_slave(0).aw.valid := io.dmi.req.valid 
    io.ps_axi_slave(0).w.valid := io.dmi.req.valid
    io.ps_axi_slave(0).ar.valid := false.B
    io.dmi.req.ready := io.ps_axi_slave(0).aw.ready && io.ps_axi_slave(0).w.ready
  }
  when(io.dmi.req.bits.op === DMConsts.dmi_OP_READ){
    io.ps_axi_slave(0).aw.valid := false.B
    io.ps_axi_slave(0).w.valid := false.B
    io.ps_axi_slave(0).ar.valid := io.dmi.req.valid
    io.dmi.req.ready := io.ps_axi_slave(0).ar.ready
  }
  io.ps_axi_slave(0).aw.bits.addr := (debugtlbase | (io.dmi.req.bits.addr << 2))
  io.ps_axi_slave(0).aw.bits.size := 2.U
  io.ps_axi_slave(0).aw.bits.len := 0.U
  io.ps_axi_slave(0).aw.bits.id := 0.U
  io.ps_axi_slave(0).w.bits.data := io.dmi.req.bits.data
  io.ps_axi_slave(0).w.bits.last := 1.U

  io.ps_axi_slave(0).ar.bits.addr := (debugtlbase | (io.dmi.req.bits.addr << 2))
  io.ps_axi_slave(0).ar.bits.size := 2.U
  io.ps_axi_slave(0).ar.bits.len := 0.U
  io.ps_axi_slave(0).ar.bits.id := 0.U

  io.dmi.resp.valid := (io.ps_axi_slave(0).r.valid | io.ps_axi_slave(0).b.valid)
  io.dmi.resp.bits.data := io.ps_axi_slave(0).r.bits.data
  io.ps_axi_slave(0).r.ready := io.dmi.resp.ready
  io.ps_axi_slave(0).b.ready := io.dmi.resp.ready
}

class Top extends Module {
  implicit val inParams = (new WithZynqAdapter).alterPartial {
    case ExtMem => MasterConfig(base= 0x10000000L, size= 0x200000L, beatBytes= 4, idBits= 4)
    case NUM_MEMORY_PORTS => 2
  }
  val tile = LazyModule(new SodorTile()(inParams)).module
  val io = IO(new Bundle {
    val success = Output(Bool())
  })
  val axi4todmi = Module(new DMItoAXI4(this)(inParams))
  tile.io.ps_slave <> axi4todmi.io.ps_axi_slave
  io.success := axi4todmi.io.success
  val dtm = Module(new SimDTM()(inParams)).connect(clock, reset.toBool, axi4todmi.io.dmi, io.success)
}

object elaborate extends ChiselFlatSpec{
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(args, () => new Top)
  }
}