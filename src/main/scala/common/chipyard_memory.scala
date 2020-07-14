package sodor.common

import chisel3._
import chisel3.util._
import chisel3.experimental._

import freechips.rocketchip.ScratchpadSlavePort
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.model.OMSRAM
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

case object SodorScratchpadBaseAddress extends Field[BigInt](0x80000000)

class SodorScratchpad(implicit p: Parameters) extends LazyModule with HasL1HellaCacheParameters {
  val baseAddress = p(SodorScratchpadBaseAddress)

  // Ports
  val address = new AddressSet(param.baseAddress, param.baseAddress + dataScratchpadSize)
  val slavePort = new ScratchpadSlavePort(address = address, coreDataBytes = wordBytes, usingAtomics = False)

  lazy val module = new SodorScratchpadImp(this)
}

class SodorScratchpadImp(outer: SodorScratchpad) extends BaseTileModuleImp(outer) {
  val io = new HellaCacheIO(outer.p)

  val dataArray = Mem(outer.dataScratchpadSize, UInt((outer.wordBytes * 8).W))

  // Slave port signals
  val slave_ready = outer.slavePort.module.io.dmem.req.ready
  val slave_resp_valid = outer.slavePort.module.io.dmem.resp.valid
  val slave_req_valid = outer.slavePort.module.io.dmem.req.valid
  val s1_slave_req_valid = RegNext(slave_req_valid)
  val slave_cmd = outer.slavePort.module.io.dmem.req.bits.cmd
  val s1_slave_cmd = RegNext(slave_cmd)

  val slave_req_addr = outer.slavePort.module.io.dmem.req.bits.addr(log2Ceil(outer.dataScratchpadSize), 0)
  val slave_req_size = outer.slavePort.module.io.dmem.req.bits.size
  val slave_read_data = outer.slavePort.module.io.dmem.resp.bits.data
  val slave_read_mask = outer.slavePort.module.io.dmem.resp.bits.mask
  val s1_slave_req_addr = RegNext(slave_req_addr)
  val s1_slave_write_kill = outer.slavePort.module.io.dmem.s1_kill
  val s1_slave_write_data = outer.slavePort.module.io.dmem.s1_data.data
  val s1_slave_write_mask = outer.slavePort.module.io.dmem.s1_data.mask

  slave_ready := true.B
  slave_read_mask := new StoreGen(slave_req_size, slave_req_addr, 0.U, outer.wordBytes).mask
  slave_read_data := dataArray.read(slave_read_addr)
  slave_resp_valid := slave_req_valid & s1_slave_cmd === M_XRD
  when (slave_req_valid & s1_slave_cmd === M_XWR & !s1_slave_write_kill) {
    printf("written: %f\n", s1_slave_write_data)
    dataArray.write(s1_slave_req_addr, s1_slave_write_data & s1_slave_write_mask)
  }

  // Core port signals
  
}