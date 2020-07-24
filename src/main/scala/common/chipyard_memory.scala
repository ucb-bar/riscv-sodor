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

class SodorScratchpad(implicit p: Parameters) extends Module {
  val io = IO(Flipped(new HellaCacheIO()))

  val coreParams = {
    class C(implicit val p: Parameters) extends HasCoreParameters
    new C
  }
  // Extract tileParams from HasNonDiplomaticTileParameters, which is the base trait of HasCoreParameters above
  val tileParams = coreParams.tileParams

  val dataArray = Mem(tileParams.dcache.get.dataScratchpadBytes, UInt((coreParams.coreDataBytes * 8).W))

  // Slave port signals
  val slave_ready = io.req.ready
  val slave_resp_valid = io.resp.valid
  val slave_req_valid = io.req.valid
  val s1_slave_req_valid = RegNext(slave_req_valid)
  val slave_cmd = io.req.bits.cmd
  val s1_slave_cmd = RegNext(slave_cmd)

  val slave_req_addr = io.req.bits.addr(log2Ceil(tileParams.dcache.get.dataScratchpadBytes), 0)
  val slave_req_size = io.req.bits.size
  val slave_read_data = io.resp.bits.data
  val slave_read_mask = io.resp.bits.mask
  val s1_slave_req_addr = RegNext(slave_req_addr)
  val s1_slave_write_kill = io.s1_kill
  val s1_slave_write_data = io.s1_data.data
  val s1_slave_write_mask = io.s1_data.mask

  slave_ready := true.B
  slave_read_mask := new StoreGen(slave_req_size, slave_req_addr, 0.U, coreParams.coreDataBytes).mask
  slave_read_data := dataArray.read(slave_req_addr)
  slave_resp_valid := slave_req_valid & s1_slave_cmd === M_XRD
  when (slave_req_valid & s1_slave_cmd === M_XWR & !s1_slave_write_kill) {
    printf("written: %x\n", s1_slave_write_data)
    dataArray.write(s1_slave_req_addr, s1_slave_write_data & s1_slave_write_mask)
  }

  // Core port signals
  
}