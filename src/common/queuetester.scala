package Sodor

import Chisel._
import Node._;

class Fame1QueueReceiver extends Module {
	val io = new Bundle {
		val fame1_queue = (new ioQueueFame1(UInt(width = 5))).flip
		val shift_reg1 = UInt(OUTPUT, width = 5)
		val shift_reg2 = UInt(OUTPUT, width = 5)
		val shift_reg3 = UInt(OUTPUT, width = 5)
		val shift_reg4 = UInt(OUTPUT, width = 5)
		val target_cycle_count = UInt(OUTPUT, width = 32)
	}
	val wait0 :: wait1 :: wait2 :: wait3 :: wait4 :: wait5 :: ctrl_done :: Nil = Enum(UInt(), 7)
	val s0 :: s1 :: s2 :: Nil = Enum(UInt(), 3)
	val ctrl_current_state = Reg(init = ctrl_done)
	val ctrl_next_state = UInt()
	ctrl_current_state := ctrl_next_state
	val take_input = Bool()
	take_input := Bool(false)
	ctrl_next_state := wait0
	when(ctrl_current_state === wait0){
	   ctrl_next_state := wait1
	}.elsewhen(ctrl_current_state === wait1){
	   ctrl_next_state := wait2
	}.elsewhen(ctrl_current_state === wait2){
	   ctrl_next_state := wait3
	}.elsewhen(ctrl_current_state === wait3){
	   ctrl_next_state := wait4
	}.elsewhen(ctrl_current_state === wait4){
	   ctrl_next_state := wait5
	}.elsewhen(ctrl_current_state === wait5){
	   ctrl_next_state := ctrl_done
	}.elsewhen(ctrl_current_state === ctrl_done){
	   take_input := Bool(true)
	   ctrl_next_state := ctrl_done
	}
	val fire_tgt_clk = io.fame1_queue.host_valid && take_input
	io.fame1_queue.host_ready := Bool(false)
	when(fire_tgt_clk){
		io.fame1_queue.host_ready := Bool(true)
	}
	//target machine
	val target_cycle_count = Reg(init = UInt(0, width = 32))
	when(fire_tgt_clk){
		target_cycle_count := target_cycle_count + UInt(1)
	}
	io.target_cycle_count := target_cycle_count
	val shift_reg0 = Reg(init = UInt(0))
	val shift_reg1 = Reg(init = UInt(0))
	val shift_reg2 = Reg(init = UInt(0))
	val shift_reg3 = Reg(init = UInt(0))
	io.shift_reg1 := shift_reg0
	io.shift_reg2 := shift_reg1
	io.shift_reg3 := shift_reg2
	io.shift_reg4 := shift_reg3
	val current_state = Reg(init = s0)
	val next_state = UInt()
	when(fire_tgt_clk){
		current_state := next_state
	}
	io.fame1_queue.target.ready := Bool(false)
	next_state := s0
	when(current_state === s0){
		next_state := s1
	}.elsewhen(current_state === s1){
		next_state := s2
	}.elsewhen(current_state === s2){
		io.fame1_queue.target.ready := Bool(true)
		next_state := s2
	}
	when(fire_tgt_clk){
		when(io.fame1_queue.target.valid && io.fame1_queue.target.ready){
			shift_reg0 := io.fame1_queue.target.bits
			shift_reg1 := shift_reg0
			shift_reg2 := shift_reg1
			shift_reg3 := shift_reg2
		}
	}
}

class QueueReceiver extends Module {
	val io = new Bundle {
		val fame1_queue = (new DecoupledIO(new RegBundle())).flip
		val shift_reg1 = UInt(OUTPUT, width = 5)
		val shift_reg2 = UInt(OUTPUT, width = 5)
		val shift_reg3 = UInt(OUTPUT, width = 5)
		val shift_reg4 = UInt(OUTPUT, width = 5)
		val mem_out = UInt(OUTPUT, width = 5)
    val target_cycle_count = UInt(OUTPUT, width = 32)

	}
	val s0 :: s1 :: s2 :: Nil = Enum(UInt(), 3)
	//target machine
	val target_cycle_count = Reg(init = UInt(0, width = 32))
  target_cycle_count := target_cycle_count + UInt(1)
	io.target_cycle_count := target_cycle_count
	val shift_reg0 = Reg(init = UInt(0))
	val shift_reg1 = Reg(init = UInt(0))
	val shift_reg2 = Reg(init = UInt(0))
	val shift_reg3 = Reg(init = UInt(0))
	io.shift_reg1 := shift_reg0
	io.shift_reg2 := shift_reg1
	io.shift_reg3 := shift_reg2
	io.shift_reg4 := shift_reg3
	val current_state = Reg(init = s0)
	val next_state = UInt()
  current_state := next_state
	io.fame1_queue.ready := Bool(false)
	next_state := s0
	when(current_state === s0){
		next_state := s1
	}.elsewhen(current_state === s1){
		next_state := s2
	}.elsewhen(current_state === s2){
		io.fame1_queue.ready := Bool(true)
		next_state := s2
	}
  when(io.fame1_queue.valid && io.fame1_queue.ready){
    shift_reg0 := io.fame1_queue.bits.data
    shift_reg1 := shift_reg0
    shift_reg2 := shift_reg1
    shift_reg3 := shift_reg2
  }

  val mem = Mem(UInt(width = 5), 1)
  val read_addr = UInt(0)
  io.mem_out := mem.read(read_addr)
  val write_en = Bool(true)
  val write_data = UInt(1)
  mem.write(read_addr, write_data)
}

class Fame1QueueTester extends Module {
	val io = new Bundle {
		val fame1_queue = new ioQueueFame1(new RegBundle)
	}
	val wait0 :: wait1 :: wait2 :: wait3 :: ctrl_done :: stop :: Nil = Enum(UInt(), 6)
	val idle :: s0 :: s1 :: s2 :: s3 :: done :: Nil = Enum(UInt(), 6)
	val current_state = Reg(init = idle)
	val ctrl_current_state = Reg(init = ctrl_done)
	val ctrl_next_state = UInt()
	ctrl_current_state := ctrl_next_state
	val do_output = Bool()
	do_output := Bool(false)
	ctrl_next_state := wait0
	when(ctrl_current_state === wait0){
		ctrl_next_state := ctrl_done
	}.elsewhen(ctrl_current_state === wait1){
		ctrl_next_state := wait2
	}.elsewhen(ctrl_current_state === wait2){
		ctrl_next_state := wait3
	}.elsewhen(ctrl_current_state === wait3){
		ctrl_next_state := ctrl_done
	}.elsewhen(ctrl_current_state === ctrl_done){
		do_output := Bool(true)
		when(current_state === done){
			ctrl_next_state := stop
		}.otherwise{
			ctrl_next_state := ctrl_done
		}
	}.elsewhen(ctrl_current_state === stop){
		ctrl_next_state := stop
	}
	val fire_tgt_clk = io.fame1_queue.host_ready && do_output
	io.fame1_queue.host_valid := Bool(false)
	when(fire_tgt_clk){
		io.fame1_queue.host_valid := Bool(true)
	}
	
	//target machine
	val next_state = UInt()
	when(fire_tgt_clk){
		current_state := next_state
	}
	//state transitions
	io.fame1_queue.target.valid := Bool(false)
	io.fame1_queue.target.bits.data := UInt(0)
	next_state := idle
	when(current_state === idle){
		next_state := s0
	}.elsewhen(current_state === s0){
		io.fame1_queue.target.bits.data := UInt(10)
		io.fame1_queue.target.valid := Bool(true)
		when(io.fame1_queue.target.ready){
			next_state := s1
		}.otherwise{
			next_state := s0
		}
	}.elsewhen(current_state === s1){
		io.fame1_queue.target.bits.data := UInt(11)
		io.fame1_queue.target.valid := Bool(true)
		when(io.fame1_queue.target.ready){
			next_state := s2
		}.otherwise{
			next_state := s1
		}
		next_state := s2
	}.elsewhen(current_state === s2){
		io.fame1_queue.target.bits.data := UInt(12)
		io.fame1_queue.target.valid := Bool(true)
		when(io.fame1_queue.target.ready){
			next_state := s3
		}.otherwise{
			next_state := s2
		}
	}.elsewhen(current_state === s3){
		io.fame1_queue.target.bits.data := UInt(13)
		io.fame1_queue.target.valid := Bool(true)
		when(io.fame1_queue.target.ready){
			next_state := done
		}.otherwise{
			next_state := s3
		}
		next_state := done
	}.elsewhen(current_state === done){
		next_state := done
	}
}
