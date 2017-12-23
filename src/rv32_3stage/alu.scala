// This is borrowed from rocket, and modified to be hardcoded to 32b.
// I will leave it as an excercise to the reader to make a parameterizable ALU
// that doesn't generate extra hardware for 32b. I also didn't carefully
// consider the function encodings. - Chris
package RV32_3stage

import chisel3._
import chisel3.util._
import config._
import Common._
import Constants._

object ALU
{
  // TODO is this the optimal encoding?
  val SZ_ALU_FN = 4
  val ALU_X    = 0.U // TODO use a more optimal decode table, which uses "???" format
  val ALU_ADD  = 0.U
  val ALU_SLL  = 1.U
  val ALU_XOR  = 4.U
  val ALU_OR   = 6.U
  val ALU_AND  = 7.U
  val ALU_SRL  = 5.U
  val ALU_SUB  = 10.U
  val ALU_SRA  = 11.U
  val ALU_SLT  = 12.U
  val ALU_SLTU = 14.U
  val ALU_COPY1= 8.U   
    
  def isSub(cmd: UInt) = cmd(3)
  def isSLTU(cmd: UInt) = cmd(1)
}
import ALU._

class ALUIO(implicit p: Parameters) extends Bundle {
  val xlen = p(xprlen)
  val fn = Input(UInt(SZ_ALU_FN.W))
  val in2 = Input(UInt(xlen.W))
  val in1 = Input(UInt(xlen.W))
  val out = Output(UInt(xlen.W))
  val adder_out = Output(UInt(xlen.W))
}

class ALU(implicit p: Parameters) extends Module
{
  val io = IO(new ALUIO)

  val msb = p(xprlen)-1

  // ADD, SUB
  val sum = io.in1 + Mux(isSub(io.fn), -io.in2, io.in2)

  // SLT, SLTU
  val less  =  Mux(io.in1(msb) === io.in2(msb), sum(msb),
              Mux(isSLTU(io.fn), io.in2(msb), io.in1(msb)))

  require(p(xprlen) == 32)
  // SLL, SRL, SRA
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

  val out_xpr_length =
    Mux(io.fn === ALU_ADD || io.fn === ALU_SUB,  sum,
    Mux(io.fn === ALU_SLT || io.fn === ALU_SLTU, less,
    Mux(io.fn === ALU_SRL || io.fn === ALU_SRA,  shout_r,
    Mux(io.fn === ALU_SLL,                       shout_l,
        bitwise_logic))))

  io.out := out_xpr_length(31,0).toUInt
  io.adder_out := sum
}
