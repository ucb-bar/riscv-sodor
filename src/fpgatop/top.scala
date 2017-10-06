package zynq

import chisel3._
import chisel3.util._
import chisel3.iotesters._
import chisel3.testers._
import config._
import Common._
import diplomacy._
import Common.Util._
import ReferenceChipBackend._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import junctions._
import unittest._
import junctions.NastiConstants._
import uncore.tilelink2._
import config.{Parameters, Field}
import RV32_3stage._

object ReferenceChipBackend {
  val initMap = new HashMap[Module, Bool]()
}

case class MasterConfig(base: Long, size: Long, beatBytes: Int, idBits: Int)
case object ExtMem extends Field[MasterConfig]
case object MMIO extends Field[MasterConfig]
case object DebugAddrSlave extends Field[MasterConfig]
class WithZynqAdapter extends Config((site, here, up) => {
  case junctions.NastiKey => junctions.NastiParameters(dataBits = 32,
      addrBits = 32,idBits = 12)
  case ExtMem => MasterConfig(base= 0x10000000L, size= 0x10000000L, beatBytes= 4, idBits= 4)
  case MMIO => MasterConfig(base= 0x40000000L, size= 0x10000L, beatBytes= 4, idBits= 4)
  case DebugAddrSlave => MasterConfig(base= 0x40000000L, size= 0x10000000L, beatBytes= 4, idBits= 4)
  case TLMonitorBuilder => (args: TLMonitorArgs) => None
  case TLCombinationalCheck => false
  case Common.xprlen => 32
  case Common.usingUser => false
  //case UnitTests => Seq(new TLMulticlientXbarTest(1,2))(this)
})

class Top extends Module {
  val inParams = new WithZynqAdapter
  inParams.alterPartial({case UnitTests => new TLMulticlientXbarTest(1,2)(inParams)})
  val tile = LazyModule(new SodorTile()(inParams)).module
  val io = IO(new Bundle {
    val ps_axi_slave = Flipped(tile.io.ps_slave.cloneType)
    val mem_axi = tile.io.mem_axi4.cloneType
  })
  io.mem_axi <> tile.io.mem_axi4
  tile.io.ps_slave <> io.ps_axi_slave
}

class TopTests extends SteppedHWIOTester {
  def reqW(addr: BigInt,data: BigInt) = {
    poke(device_under_test.io.ps_axi_slave(0).aw.valid, 1)
    poke(device_under_test.io.ps_axi_slave(0).w.valid, 1)
    poke(device_under_test.io.ps_axi_slave(0).w.bits.last, 1)
    poke(device_under_test.io.ps_axi_slave(0).aw.bits.addr, addr)
    poke(device_under_test.io.ps_axi_slave(0).aw.bits.len, 0L)
    poke(device_under_test.io.ps_axi_slave(0).aw.bits.size, 2L)
    poke(device_under_test.io.ps_axi_slave(0).aw.bits.id, 0L)
    poke(device_under_test.io.ps_axi_slave(0).w.bits.data, data)
    poke(device_under_test.io.ps_axi_slave(0).b.ready, 1)
  }
  def reqR(addr: BigInt) = {
    poke(device_under_test.io.ps_axi_slave(0).ar.valid, 1)
    poke(device_under_test.io.ps_axi_slave(0).ar.bits.addr, addr)
    poke(device_under_test.io.ps_axi_slave(0).ar.bits.len, 0L)
    poke(device_under_test.io.ps_axi_slave(0).ar.bits.size, 2L) 
  }
  def checkReqW = {
    expect(device_under_test.io.ps_axi_slave(0).w.ready, true)
    expect(device_under_test.io.ps_axi_slave(0).aw.ready, true)
  }
  def resetWReq = {
    poke(device_under_test.io.ps_axi_slave(0).aw.valid, 0)
    poke(device_under_test.io.ps_axi_slave(0).w.valid, 0)
  }
  def resetRReq = {
    poke(device_under_test.io.ps_axi_slave(0).ar.valid, 0)
  }
  def checkWResp = {
    expect(device_under_test.io.ps_axi_slave(0).b.valid, true)
  }
  def checkRResp(data: BigInt) = {
    expect(device_under_test.io.ps_axi_slave(0).r.bits.data, data)
    expect(device_under_test.io.ps_axi_slave(0).r.valid, true)
  }  
  def memReadResp(addr: BigInt,data: BigInt,id: BigInt) = {
    expect(device_under_test.io.mem_axi(0).ar.bits.addr , addr)
    expect(device_under_test.io.mem_axi(0).ar.valid , true)
    poke(device_under_test.io.mem_axi(0).r.valid, 1)
    poke(device_under_test.io.mem_axi(0).r.bits.id, id)
    poke(device_under_test.io.mem_axi(0).r.bits.last, 1)
    poke(device_under_test.io.mem_axi(0).r.bits.data, data)
  }
  def memResetReadResp = {
    poke(device_under_test.io.mem_axi(0).r.valid, 0)
  }
  def memResetWriteResp = {
    poke(device_under_test.io.mem_axi(0).b.valid, 0)
  }
  def memWriteResp(addr: BigInt,data: BigInt,id: BigInt) = {
    expect(device_under_test.io.mem_axi(0).aw.valid, true)
    expect(device_under_test.io.mem_axi(0).w.valid, true)
    expect(device_under_test.io.mem_axi(0).aw.bits.addr, addr)
    expect(device_under_test.io.mem_axi(0).w.bits.data, data)
    expect(device_under_test.io.mem_axi(0).b.ready, true)
    poke(device_under_test.io.mem_axi(0).b.valid, 1)
    poke(device_under_test.io.mem_axi(0).b.bits.id, id)
  }

  def checkDCPath = {
      reqW(0x40000110L,0x1L)
    step(1)
      resetWReq
    step(1)
      checkWResp
    step(1)
      memReadResp(0x10000000L,0x00002097L,1)
      //memReadResp(0x10000000L,0x400000b7L,1)
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x10000004L,0x0aa10113L,1)
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x10000008L,0x0020a023L,1)
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x1000000CL,0x0000af03L,1)
    step(1)
      memResetReadResp
      memWriteResp(0x10002000L,0xaaL,2)
    step(1)
      memResetWriteResp
    step(1)
      memReadResp(0x10000010L,0x0aae8e93L,1)
    step(1)
      memReadResp(0x10002000L,0xaaL,2)
    step(1)
      memResetReadResp
    step(6)
  }
  def checkDebugMem = {
      reqW(0x400000E4,0x10002000L)
    step(1)
      resetWReq
    step(1)
      checkWResp
      reqW(0x400000F0,1)
    step(1)
      resetWReq
    step(2)
      memWriteResp(0x10002000,1,0)
    step(1)
      memResetWriteResp
    step(1)
      checkWResp
    step(1)
      reqR(0x400000F0)
    step(1)
      resetRReq
    step(1)
      memReadResp(0x10002000L,0xaa,0)
    step(1)
      memResetReadResp
    step(2)
      checkRResp(0xaa)
    step(2)
      reqW(0x400000E4,0x10002000L)
    step(1)
      resetWReq
    step(1)
      checkWResp
      reqW(0x400000F0,1)
    step(1)
      resetWReq
    step(2)
      memWriteResp(0x10002000,1,0)
    step(1)
      memResetWriteResp
    step(1)
      checkWResp
    step(1)
      reqR(0x400000F0)
    step(1)
      resetRReq
    step(1)
      memReadResp(0x10002000L,0xaa,0)
    step(1)
      memResetReadResp
    step(2)
      checkRResp(0xaa)
    step(1)
      reqW(0x400000E4,1)
    step(1)
      resetWReq
    step(1)
      checkWResp
      reqR(0x400000E0)
    step(1)
      resetRReq
    step(2)
      checkRResp(0x040404L)

  }
  def checkLWSW = {
    reqW(0x40000110L,0x1L)
    step(1)
      resetWReq
    step(1)
      checkWResp
    step(1)
      memReadResp(0x10000000L,0x400000b7L,1)
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x10000004L,0x00c0a103L,1)
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x10000008L,0x0080a103L,1)
    step(1)
      memReadResp(0x4000000CL,0xaL,2)
    step(1)
      memResetReadResp
    step(1)
      memReadResp(0x1000000CL,0x0020a023L,1)
    step(1)
      memResetReadResp
    step(1)
      memReadResp(0x40000008L,0xbL,2)
    step(1)
      memResetReadResp
    step(2)
      memWriteResp(0x40000000L,0xbL,2)
    step(1)
      memResetWriteResp
    step(6)
  }
  def checkJump = {
/*
10000000 <star-0x14>:
10000000: 400000b7            lui ra,0x40000
10000004: 0000a223            sw  zero,4(ra) # 40000004 <__global_pointer$+0x2fffe7d8>
10000008: 00600193            li  gp,6
1000000c: 00a00213            li  tp,10
10000010: 00100293            li  t0,1

10000014 <star>:
10000014: 0080a103            lw  sp,8(ra)
10000018: 00511663            bne sp,t0,10000024 <temp>
1000001c: 0030a023            sw  gp,0(ra)
10000020: ff5ff06f            j 10000014 <star>

10000024 <temp>:
10000024: 0040a023            sw  tp,0(ra)
10000028: fedff06f            j 10000014 <star>
*/
    reqW(0x40000110L,0x1L)
    step(1)
      resetWReq
    step(1)
      checkWResp
    step(1)
      memReadResp(0x10000000L,0x400000b7L,1)  // lui ra,0x40000
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x10000004L,0x0000a223L,1)  // sw zero,4(ra)
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x10000008L,0x00600193L,1)  // li gp,6
    step(1)
      memResetReadResp
      memWriteResp(0x40000004L,0x0L,2)
    step(1)
      memResetWriteResp
    step(1)
      memReadResp(0x1000000CL,0x00a00213L,1)  // li tp,10
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x10000010L,0x00100293L,1)  // li t0,1
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x10000014L,0x0080a103L,1)  // lw sp,8(ra)
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x10000018L,0x00511663L,1)  // bne  sp,t0,10000024
    step(1)
      memReadResp(0x40000008L,0x0L,2)
    step(1)
      memResetReadResp
//      poke(device_under_test.io.mem_axi(0).ar.ready , 0)
    step(1)
//      poke(device_under_test.io.mem_axi(0).ar.ready , 1)
      memReadResp(0x1000001CL,0x0030a023L,1)  // sw gp,0(ra)
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x10000024L,0x0040a023L,1)  // sw tp,0(ra)
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x10000028L,0xfedff06fL,1)  // j  10000014
    step(1)
      memResetReadResp
      memWriteResp(0x40000000L,0xaL,2)
    step(1)
      memResetWriteResp
      poke(device_under_test.io.mem_axi(0).ar.ready , 0)
    step(8)
      poke(device_under_test.io.mem_axi(0).ar.ready , 1)
      memReadResp(0x1000002CL,0xc3bc6be6L,1)  // sw tp,0(ra)
    step(1)
      memResetReadResp
      poke(device_under_test.io.mem_axi(0).ar.ready , 0)
    step(8)
      poke(device_under_test.io.mem_axi(0).ar.ready , 1)
      memReadResp(0x10000014L,0x0080a103L,1)  // lw sp,8(ra)
    step(1)
      memResetReadResp
    step(5)
  }

  enable_scala_debug = false
  enable_printf_debug = false
  val device_under_test = Module(new Top())
    
    poke(device_under_test.io.ps_axi_slave(0).aw.bits.burst, 0L)
    poke(device_under_test.io.ps_axi_slave(0).ar.bits.burst, 0L)
    poke(device_under_test.io.ps_axi_slave(0).r.ready, 1)
    poke(device_under_test.io.mem_axi(0).aw.ready , 1)
    poke(device_under_test.io.mem_axi(0).w.ready , 1)
    poke(device_under_test.io.mem_axi(0).ar.ready , 1)
    
    checkDCPath
    //checkDebugMem
    //checkLWSW
    //checkJump
}

class TestHarnessTester(dut: TestHarness) extends PeekPokeTester(dut) {
  when(dut.io.success === true.B){
    printf("done\n")
  }
}

object elaborate extends ChiselFlatSpec{
  def main(args: Array[String]): Unit = {
    implicit val inParams = new WithZynqAdapter
    if(!args.isEmpty && args(0) == "testtop")
      assertTesterPasses(new TopTests,additionalVResources = Seq("/SyncMem.sv"))
/*    else if(!args.isEmpty && args(0) == "testxbar"){
      chisel3.iotesters.Driver(() => new TestHarness()(inParams),"firrtl") {
        c => new TestHarnessTester(c)
      }should be(true)
    }*/
    else 
      chisel3.Driver.execute(args, () => new Top)
  }
}