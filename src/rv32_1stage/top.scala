package Sodor

import Chisel._
import Node._
import Constants._
import Common._
import Common.Util._
import ReferenceChipBackend._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap


object ReferenceChipBackend {
  val initMap = new HashMap[Module, Bool]()
}

class TopIO() extends Bundle  {
  val debug = new Common.DebugIO
  val htif  = new Common.HTIFIO()
  val shift_reg = UInt(OUTPUT, width = 5)
  val mem_out = UInt(OUTPUT, width = 5)
  val target_cycle_count = UInt(OUTPUT, width = 32)
}


class Top extends Module
{
  val io = new TopIO()
  
  val queueTester = Module(new Fame1QueueTester())
  val queueReceiver = Module(new Fame1Wrapper(new QueueReceiver()))
  val fameQueue = Module(new QueueFame1(16)(new RegBundle()))

  queueTester.io.fame1_queue <> fameQueue.io.enq
  queueReceiver.fame1DecoupledIOs("fame1_queue") <> fameQueue.io.deq
  io.shift_reg := queueReceiver.fame1OtherIO("shift_reg4")
  io.mem_out := queueReceiver.fame1OtherIO("mem_out")
  io.target_cycle_count := queueReceiver.fame1OtherIO("target_cycle_count")
}

object elaborate {
  def main(args: Array[String]): Unit = {
    chiselMain(args ++ Array("--backend", "Chisel.Fame1CppBackend") , () => Module(new Top()))
  }
}

/*
class TopIO() extends Bundle  {
  val debug = new Common.DebugIO
  val htif  = new Common.HTIFIO()
  val shift_reg = UInt(OUTPUT, width = 5)
  val target_cycle_count = UInt(OUTPUT, width = 32)
}


class Top extends Module
{
  val io = new TopIO()
  
  val queueTester = Module(new Fame1QueueTester())
  val queueReceiver = Module(new Fame1QueueReceiver())
  val fameQueue = Module(new QueueFame1(16)(UInt(width=5)))

  queueTester.io.fame1_queue <> fameQueue.io.enq
  queueReceiver.io.fame1_queue <> fameQueue.io.deq
  io.shift_reg := queueReceiver.io.shift_reg4
  io.target_cycle_count := queueReceiver.io.target_cycle_count
}

object elaborate {
  def main(args: Array[String]): Unit = {
    println("DEBUG0")
    chiselMain(args ++ Array("--backend", "Chisel.Fame1CppBackend") , () => Module(new Top()))
  }
}
*/
/*class TopIO() extends Bundle  {
  val debug = new Common.DebugIO
  val htif  = new Common.HTIFIO()
  val testreg1 = UInt(OUTPUT, width =32)
  val testreg2 = UInt(OUTPUT, width =32)
}


class Top extends Module
{
  val io = new TopIO()
  
  /*
  val regtester1 = Module(new RegTester())
	val regtester2 = Module(new RegTester())
	val reg1 = Reg(init = UInt(0, width = 32))
	val reg2 = Reg(init = UInt(0, width = 32))
	regtester1.io.in.bits.data := reg1
	regtester2.io.in.bits.data := reg2
	reg1 := regtester2.io.out.bits.data
	reg2 := regtester1.io.out.bits.data
	io.testreg1 := reg1
	io.testreg2 := reg2*/
	
	val regtester1 = Module(new Fame1RegTester())
	val regtester2 = Module(new Fame1Wrapper(new RegTester()))

	val queue1 = Module(new Queue(new RegBundle(), 4))
	val queue2 = Module(new Queue(new RegBundle(), 4))
	regtester1.io.in <> queue1.io.deq
	regtester2.fame1REGIOs("in") <> queue2.io.deq
	
	val was_reset = Reg(init = Bool(true))
	was_reset := Bool(false)
	queue1.io.enq.valid := regtester2.fame1REGIOs("out").valid
	queue1.io.enq.bits := regtester2.fame1REGIOs("out").bits
	regtester2.fame1REGIOs("out").ready := queue1.io.enq.ready
	queue2.io.enq.valid := regtester1.io.out.valid
	queue2.io.enq.bits := regtester1.io.out.bits
	regtester1.io.out.ready := queue2.io.enq.ready
	when(was_reset){
		queue1.io.enq.valid := Bool(true)
		queue1.io.enq.bits.data := UInt(0)
		queue2.io.enq.valid := Bool(true)
		queue2.io.enq.bits.data := UInt(0)
	}
	
	io.testreg1 := queue1.io.deq.bits.data
	io.testreg2 := queue2.io.deq.bits.data
}

object elaborate {
  def main(args: Array[String]): Unit = {
    println("DEBUG0")
    chiselMain(args ++ Array("--backend", "Chisel.Fame1CppBackend") , () => Module(new Top()))
  }
}
*/

/*
class TopIO() extends Bundle  {
  val debug = new Common.DebugIO
  val htif  = new Common.HTIFIO()
}

class Top extends Module
{
   val io = new TopIO()

   implicit val sodor_conf = SodorConfiguration()

   val reset_signal = Reg(next=Reg(next=io.htif.reset))
   val tile = Module(new SodorTile())
  
   tile.io.host.reset := reset_signal
   tile.io.host.pcr_req <> Queue(io.htif.pcr_req)
   io.htif.pcr_rep <> Queue(tile.io.host.pcr_rep)

   tile.io.host.mem_req <> Queue(io.htif.mem_req)
   io.htif.mem_rep <> tile.io.host.mem_rep

   io.debug.error_mode := Reg(next=tile.io.host.debug.error_mode)
}


object elaborate {
  def main(args: Array[String]): Unit = {
    chiselMain(args, () => Module(new Top()))
  }
}*/
