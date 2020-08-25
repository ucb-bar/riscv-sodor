package sodor.common

import chisel3._
import chisel3.util._
import chisel3.experimental._

import freechips.rocketchip.rocket._
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.model.OMSRAM
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class SodorScratchpadAdapter(implicit p: Parameters, implicit val sodorConf: SodorConfiguration) extends Module {
  // Parameter traits
  val coreParams = {
    class C(implicit val p: Parameters) extends HasCoreParameters
    new C
  }
  // Extract tileParams from HasNonDiplomaticTileParameters, which is the base trait of HasCoreParameters above
  val tileParams = coreParams.tileParams
  // Sodor config class

  // Sodor constants
  val sodorConst = {
    class S extends MemoryOpConstants
    new S
  }

  val io = IO(new Bundle() {
    val slavePort = Flipped(new HellaCacheIO())
    val memPort = new MemPortIo(data_width = coreParams.coreDataBits)
  })

  // ===================
  // Slave port signals
  val slave_req_ready = io.slavePort.req.ready
  val s2_slave_resp_valid = io.slavePort.resp.valid
  val slave_req_valid = io.slavePort.req.valid

  val slave_cmd = io.slavePort.req.bits.cmd
  val slave_req_addr = io.slavePort.req.bits.addr(log2Ceil(tileParams.dcache.get.dataScratchpadBytes), 0)
  val slave_req_size = io.slavePort.req.bits.size
  val slave_req_signed = io.slavePort.req.bits.signed

  // All request are delayed for one cycle to avoid being killed
  val s1_slave_write_kill = io.slavePort.s1_kill
  val s1_slave_write_data = io.slavePort.s1_data.data
  val s1_slave_write_mask = io.slavePort.s1_data.mask

  val s1_slave_req_valid = RegNext(slave_req_valid)
  val s1_slave_cmd = RegNext(slave_cmd)
  val s1_slave_req_addr = RegNext(slave_req_addr)
  val s1_slave_req_size = RegNext(slave_req_size)
  val s1_slave_req_signed = RegNext(slave_req_signed)

  // Note that ScratchpadSlavePort requires 2-cycle delay, or it won't even send the response
  val s2_slave_read_data = io.slavePort.resp.bits.data_raw
  val s2_slave_read_mask = io.slavePort.resp.bits.mask

  val s2_nack = io.slavePort.s2_nack

  // Tie anything not defined below to DontCare
  io.slavePort := DontCare

  // ===================
  // HellaCacheIO to MemPortIo logic
  // Connect valid & ready bits
  slave_req_ready := io.memPort.req.ready
  io.memPort.req.valid := s1_slave_req_valid & (s1_slave_cmd === M_XRD || !s1_slave_write_kill)
  s2_slave_resp_valid := RegNext(io.memPort.resp.valid)

  // Connect read info
  s2_slave_read_mask := RegNext(new StoreGen(s1_slave_req_size, s1_slave_req_addr, 0.U, coreParams.coreDataBytes).mask)
  s2_slave_read_data := RegNext(io.memPort.resp.bits.data)

  // Connect write info
  io.memPort.req.bits.addr := s1_slave_req_addr
  io.memPort.req.bits.data := s1_slave_write_data

  // Other connections
  s2_nack := false.B
  io.memPort.req.bits.fcn := Mux(s1_slave_cmd === M_XRD, sodorConst.M_XRD, sodorConst.M_XWR)
  // Since we don't have dword here (the bus only has 32 bits), s1_slave_req_size <= 2.
  // The expression below convert TileLink size and signedness to Sodor type.
  io.memPort.req.bits.typ := Cat(~s1_slave_req_signed, s1_slave_req_size + 1.U)
}

// This class simply route all memory request that doesn't belong to the scratchpad.
// DO NOT USE this adapter if the master support multiple inflight request or it may break.
class SodorRequestRouter(cacheAddress: AddressSet)(implicit val conf: SodorConfiguration) extends Module {
  val io = IO(new Bundle() {
    val masterPort = new MemPortIo(data_width = conf.xprlen)
    val scratchPort = new MemPortIo(data_width = conf.xprlen)
    val corePort = Flipped(new MemPortIo(data_width = conf.xprlen))
  })

  val in_range = cacheAddress.contains(io.corePort.req.bits.addr)

  // Unfinished request: if the memory request switch from bus to scratchpad with inflight request, prevent 
  // next request from being send until the previous request returned. 
  val inflight_request = RegInit(false.B)
  val scratchpad_request = RegInit(false.B)
  when (io.corePort.req.fire() && !io.corePort.resp.valid) {
    inflight_request := true.B
    scratchpad_request := in_range
  } .elsewhen (io.corePort.resp.valid) {
    inflight_request := false.B
  }
  val range_switched = inflight_request && (scratchpad_request ^ in_range)

  // Connect other signals
  io.masterPort.req.bits <> io.corePort.req.bits
  io.scratchPort.req.bits <> io.corePort.req.bits

  // Connect valid signal 
  io.masterPort.req.valid := io.corePort.req.valid && !in_range && !range_switched
  io.scratchPort.req.valid := io.corePort.req.valid && in_range && !range_switched

  // Mux ready and request signal
  io.corePort.req.ready := Mux(in_range, io.scratchPort.req.ready, io.masterPort.req.ready) && !range_switched
  io.corePort.resp.bits := Mux(Mux(inflight_request, scratchpad_request, in_range), io.scratchPort.resp.bits, io.masterPort.resp.bits)
  io.corePort.resp.valid := Mux(Mux(inflight_request, scratchpad_request, in_range), io.scratchPort.resp.valid, io.masterPort.resp.valid)
}
