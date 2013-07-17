// This is borrowed from rocket, and modified to be hardcoded to 32b.
// I will leave it as an excercise to the reader to make a parameterizable ALU
// that doesn't generate extra hardware for 32b.
package Sodor

import Chisel._
import Node._
import Common._
import Constants._
//import Instructions._

object ALU
{
  val SZ_ALU_FN = 4
//  val FN_X    = Bits("b????")
  val FN_X    = Bits(0, SZ_ALU_FN)
  val FN_ADD  = Bits(0, SZ_ALU_FN)
  val FN_SL   = Bits(1, SZ_ALU_FN)
  val FN_XOR  = Bits(4, SZ_ALU_FN)
  val FN_OR   = Bits(6, SZ_ALU_FN)
  val FN_AND  = Bits(7, SZ_ALU_FN)
  val FN_SR   = Bits(5, SZ_ALU_FN)
  val FN_SUB  = Bits(8, SZ_ALU_FN)
  val FN_SLT  = Bits(10, SZ_ALU_FN)
  val FN_SLTU = Bits(11, SZ_ALU_FN)
  val FN_SRA  = Bits(13, SZ_ALU_FN)
  val FN_OP2  = Bits(15, SZ_ALU_FN)

  val FN_DIV  = FN_XOR
  val FN_DIVU = FN_SR
  val FN_REM  = FN_OR
  val FN_REMU = FN_AND

  val FN_MUL    = FN_ADD
  val FN_MULH   = FN_SL
  val FN_MULHSU = FN_SLT
  val FN_MULHU  = FN_SLTU

  def isMulFN(fn: Bits, cmp: Bits) = fn(1,0) === cmp(1,0)
  def isSub(cmd: Bits) = cmd(3)
  def isSLTU(cmd: Bits) = cmd(0)
}
import ALU._

class ALUIO(implicit conf: SodorConfiguration) extends Bundle {
//  val dw = Bits(INPUT, SZ_DW)
  val fn = Bits(INPUT, SZ_ALU_FN)
  val in2 = UFix(INPUT, conf.xprlen)
  val in1 = UFix(INPUT, conf.xprlen)
  val out = UFix(OUTPUT, conf.xprlen)
  val adder_out = UFix(OUTPUT, conf.xprlen)
}

class ALU(implicit conf: SodorConfiguration) extends Mod
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
//  val shamt = Cat(io.in2(5) & (io.dw === DW_64), io.in2(4,0)).toUFix
//  val shin_hi_32 = Mux(isSub(io.fn), Fill(32, io.in1(31)), UFix(0,32))
//  val shin_hi = Mux(io.dw === DW_64, io.in1(63,32), shin_hi_32) 
//  val shin_hi = shin_hi_32
//  val shin_r = Cat(shin_hi, io.in1(31,0))
  val shamt = io.in2(4,0).toUFix
  val shin_r = io.in1(31,0)
  val shin = Mux(io.fn === FN_SR  || io.fn === FN_SRA, shin_r, Reverse(shin_r))
  val shout_r = (Cat(isSub(io.fn) & shin(msb), shin).toFix >> shamt)(msb,0)
  val shout_l = Reverse(shout_r)

  val bitwise_logic =
    Mux(io.fn === FN_AND, io.in1 & io.in2,
    Mux(io.fn === FN_OR,  io.in1 | io.in2,
    Mux(io.fn === FN_XOR, io.in1 ^ io.in2,
        io.in2))) // FN_OP2

//  val out64 =
  val out_xpr_length =
    Mux(io.fn === FN_ADD || io.fn === FN_SUB,  sum,
    Mux(io.fn === FN_SLT || io.fn === FN_SLTU, less,
    Mux(io.fn === FN_SR  || io.fn === FN_SRA,  shout_r,
    Mux(io.fn === FN_SL,                       shout_l,
        bitwise_logic))))

//  val out_hi = Mux(io.dw === DW_64, out64(63,32), Fill(32, out64(31)))
//  io.out := Cat(out_hi, out64(31,0)).toUFix
  io.out := out_xpr_length(31,0).toUFix
  io.adder_out := sum
}
