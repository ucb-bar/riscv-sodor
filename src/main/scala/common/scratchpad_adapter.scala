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

class SodorScratchpadAdapter(implicit p: Parameters, implicit val sodorConf: SodorCoreParams) extends CoreModule {
  val io = IO(new Bundle() {
    val slavePort = Flipped(new HellaCacheIO())
    val memPort = new MemPortIo(data_width = coreDataBits)
  })

  // ===================
  // Slave port signals
  val slave_req_ready = io.slavePort.req.ready
  val s2_slave_resp_valid = io.slavePort.resp.valid
  val slave_req_valid = io.slavePort.req.valid

  val slave_cmd = io.slavePort.req.bits.cmd
  val slave_req = io.slavePort.req.bits

  // All request are delayed for one cycle to avoid being killed
  val s1_slave_write_kill = io.slavePort.s1_kill
  val s1_slave_write_data = io.slavePort.s1_data.data
  val s1_slave_write_mask = io.slavePort.s1_data.mask

  val s1_slave_req_valid = RegNext(slave_req_valid, false.B)
  val s1_slave_cmd = RegNext(slave_cmd)
  val s1_slave_req = RegNext(slave_req)

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
  s2_slave_resp_valid := RegNext(io.memPort.resp.valid, false.B)

  // Connect read info
  s2_slave_read_mask := RegNext(new StoreGen(s1_slave_req.size, s1_slave_req.addr, 0.U, coreDataBytes).mask)
  s2_slave_read_data := RegNext(io.memPort.resp.bits.data)

  // Connect write info
  io.memPort.req.bits.addr := s1_slave_req.addr
  io.memPort.req.bits.data := s1_slave_write_data

  // Other connections
  s2_nack := false.B
  io.memPort.req.bits.fcn := Mux(s1_slave_cmd === M_XRD, M_XRD, M_XWR)
  // Since we don't have dword here (the bus only has 32 bits), s1_slave_req.size <= 2.
  // The expression below convert TileLink size and signedness to Sodor type.
  require(io.slavePort.req.bits.addr.getWidth == 32, "Slave port only support 32 bit address")
  assert (s1_slave_req.size <= 2.U, "Slave port received a bus request with unsupported size: %d", s1_slave_req.size)
  io.memPort.req.bits.setType(s1_slave_req.signed, s1_slave_req.size)
}

// This class simply route all memory request that doesn't belong to the scratchpad
class SodorRequestRouter(cacheAddress: AddressSet)(implicit val conf: SodorCoreParams) extends Module {
  val io = IO(new Bundle() {
    val masterPort = new MemPortIo(data_width = conf.xprlen)
    val scratchPort = new MemPortIo(data_width = conf.xprlen)
    val corePort = Flipped(new MemPortIo(data_width = conf.xprlen))
    val respAddress = Input(UInt(conf.xprlen.W))
  })

  val in_range = cacheAddress.contains(io.corePort.req.bits.addr)

  // Connect other signals
  io.masterPort.req.bits <> io.corePort.req.bits
  io.scratchPort.req.bits <> io.corePort.req.bits

  // Connect valid signal 
  io.masterPort.req.valid := io.corePort.req.valid & !in_range
  io.scratchPort.req.valid := io.corePort.req.valid & in_range

  // Mux ready and request signal
  io.corePort.req.ready := Mux(in_range, io.scratchPort.req.ready, io.masterPort.req.ready)
  // Use respAddress to route response
  val resp_in_range = cacheAddress.contains(io.respAddress)
  io.corePort.resp.bits := Mux(resp_in_range, io.scratchPort.resp.bits, io.masterPort.resp.bits)
  io.corePort.resp.valid := Mux(resp_in_range, io.scratchPort.resp.valid, io.masterPort.resp.valid)
}
