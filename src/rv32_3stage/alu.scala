// This is borrowed from rocket, and modified to be hardcoded to 32b.
// I will leave it as an excercise to the reader to make a parameterizable ALU
// that doesn't generate extra hardware for 32b. I also didn't carefully
// consider the function encodings. - Chris
package Sodor

import Chisel._
import Node._
import Common._
import Constants._

object ALU
{
  // TODO is this the optimal encoding?
  val SZ_ALU_FN = 4
//  val ALU_X    = Bits("b????")
  val ALU_X    = Bits(0) // TODO use a more optimal decode table, which uses "???" format
  val ALU_ADD  = Bits(0)
  val ALU_SLL  = Bits(1)
  val ALU_XOR  = Bits(4)
  val ALU_OR   = Bits(6)
  val ALU_AND  = Bits(7)
  val ALU_SRL  = Bits(5)
//  val FN_SEQ  = Bits(8)
//  val FN_SNE  = Bits(9)
  val ALU_SUB  = Bits(10)
  val ALU_SRA  = Bits(11)
  val ALU_SLT  = Bits(12)
//  val FN_SGE  = Bits(13)
  val ALU_SLTU = Bits(14)
//  val FN_SGEU = Bits(15)
  val ALU_COPY1= Bits(8)   
    


  val ALU_DIV  = ALU_XOR
  val ALU_DIVU = ALU_SRL
  val ALU_REM  = ALU_OR
  val ALU_REMU = ALU_AND

  val ALU_MUL    = ALU_ADD
  val ALU_MULH   = ALU_SLL
  val ALU_MULHSU = ALU_SLT
  val ALU_MULHU  = ALU_SLTU

  def isMulFN(fn: Bits, cmp: Bits) = fn(1,0) === cmp(1,0)
  def isSub(cmd: Bits) = cmd(3)
  def isSLTU(cmd: Bits) = cmd(0)
}
import ALU._

class ALUIO(implicit conf: SodorConfiguration) extends Bundle {
//  val dw = Bits(INPUT, SZ_DW)
  val fn = Bits(INPUT, SZ_ALU_FN)
  val in2 = UInt(INPUT, conf.xprlen)
  val in1 = UInt(INPUT, conf.xprlen)
  val out = UInt(OUTPUT, conf.xprlen)
  val adder_out = UInt(OUTPUT, conf.xprlen)
}

class ALU(implicit conf: SodorConfiguration) extends Module
{
  val io = new ALUIO

  val msb = conf.xprlen-1

  // ADD, SUB
  val sum = io.in1 + Mux(isSub(io.fn), -io.in2, io.in2)

  // SLT, SLTU
  val less  = Mux(io.in1(msb) === io.in2(msb), sum(msb),
              Mux(isSLTU(io.fn), io.in2(msb), io.in1(msb)))

  // SLL, SRL, SRA
  require(conf.xprlen == 32)
//  val shamt = Cat(io.in2(5) & (io.dw === DW_64), io.in2(4,0)).toUInt
//  val shin_hi_32 = Mux(isSub(io.fn), Fill(32, io.in1(31)), UInt(0,32))
//  val shin_hi = Mux(io.dw === DW_64, io.in1(63,32), shin_hi_32) 
//  val shin_hi = shin_hi_32
//  val shin_r = Cat(shin_hi, io.in1(31,0))
  val shamt = io.in2(4,0).toUInt
  val shin_r = io.in1(31,0)
  val shin = Mux(io.fn === ALU_SRL  || io.fn === ALU_SRA, shin_r, Reverse(shin_r))
  val shout_r = (Cat(isSub(io.fn) & shin(msb), shin).toSInt >> shamt)(msb,0)
  val shout_l = Reverse(shout_r)

  val bitwise_logic =
    Mux(io.fn === ALU_AND, io.in1 & io.in2,
    Mux(io.fn === ALU_OR,  io.in1 | io.in2,
    Mux(io.fn === ALU_XOR, io.in1 ^ io.in2,
                           io.in1))) // ALU_COPY1

//  val out64 =
  val out_xpr_length =
    Mux(io.fn === ALU_ADD || io.fn === ALU_SUB,  sum,
    Mux(io.fn === ALU_SLT || io.fn === ALU_SLTU, less,
    Mux(io.fn === ALU_SRL || io.fn === ALU_SRA,  shout_r,
    Mux(io.fn === ALU_SLL,                       shout_l,
        bitwise_logic))))

//  val out_hi = Mux(io.dw === DW_64, out64(63,32), Fill(32, out64(31)))
//  io.out := Cat(out_hi, out64(31,0)).toUInt
  io.out := out_xpr_length(31,0).toUInt
  io.adder_out := sum
}
