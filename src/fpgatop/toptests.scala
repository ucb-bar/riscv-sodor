package zynq

import chisel3._
import chisel3.iotesters._

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
      reqW(0x40000110L,0x1L) // Pull sodor out of reset
    step(1)
      resetWReq
    step(1)
      checkWResp
    step(1)
      memReadResp(0x10000000L,0x00002097L,1) // auipc   ra, 0x2
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x10000004L,0x0aa10113L,1) // addi    sp, sp, 170
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x10000008L,0x0020a023L,1) // sw      sp, 0(ra)
    step(1)
      memResetReadResp
    step(2)
      memReadResp(0x1000000CL,0x0000af03L,1) // lw      t5, 0(ra)
    step(1)
      memResetReadResp
      memWriteResp(0x10002000L,0xaaL,2)
    step(1)
      memResetWriteResp
    step(1)
      memReadResp(0x10000010L,0x0aae8e93L,1) // addi    t4, t4, 170
    step(1)
      memReadResp(0x10002000L,0xaaL,2)
    step(1)
      memResetReadResp
    step(6)
  }
  def checkDebugMem = {
      reqW(0x400000E4,0x10002000L) // System Bus Address
    step(1)
      resetWReq
    step(1)
      checkWResp
      reqW(0x400000F0,1) // System Bus Data
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
      reqR(0x400000E0) // System Bus Access Control and Status
    step(1)
      resetRReq
    step(2)
      checkRResp(0x040404L)

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
    step(1)
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
    //checkJump
}