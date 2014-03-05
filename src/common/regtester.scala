package Sodor

import Chisel._
import Node._
import Common._

class RegTester extends Module
{
	val io = new Bundle {
		val out = new REGIO(new RegBundle())
		val in = new REGIO(new RegBundle()).flip
		val current_state = UInt(OUTPUT, width = 32)
	}
	val current_state = Reg(init = UInt(0, width = 32))
	val next_state = UInt()
	current_state := next_state
	next_state := current_state + io.in.bits.data + UInt(1)
	io.out.bits.data := current_state
	io.current_state := current_state
}

class Fame1RegTester extends Module
{
  val io = new Bundle {
    val out = new DecoupledIO(new RegBundle())
    val in = new DecoupledIO(new RegBundle()).flip
  }
  
  val counter = Reg(init = UInt(0, width = 1))
  counter := counter + UInt(1)
  val fireClk = Bool()
  fireClk := counter.toBool && io.out.ready && io.in.valid
  io.out.valid := fireClk
  io.in.ready := fireClk
  
  //target machine
  val current_state = Reg(init = UInt(0, width = 32))
	val next_state = UInt()
	when(fireClk){
	  current_state := next_state
	}
	next_state := current_state + io.in.bits.data + UInt(1)
	io.out.bits.data := current_state
}
