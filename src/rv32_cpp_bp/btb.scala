//****************************************************************************
// RISCV Processor Branch Target Buffer / Predictor
//----------------------------------------------------------------------------
//
// Eric Love
// 2014 Feb 13

package Sodor
{

import Chisel._
import Node._

import Constants._
import Common._


class BTBIo(implicit conf: SodorConfiguration) extends Bundle {
  val if_pc_reg           = UInt(INPUT, conf.xprlen)
  
  val if_pred_taken  = Bool(OUTPUT)
  val if_pred_target = UInt(OUTPUT, conf.xprlen)
  
  val exe_reg_pc            = UInt(INPUT, conf.xprlen)
  val exe_pc_next           = UInt(INPUT, conf.xprlen)
  val exe_reg_ctrl_br_type  = UInt(INPUT, width=BR_N.width)
  val exe_pc_sel            = UInt(INPUT, width=PC_PLUS4.width)
  val exe_reg_pred_taken    = Bool(INPUT)
}


class BTB(implicit conf: SodorConfiguration) extends Module {
  val io = new BTBIo()

  val BTB_ENTRIES = 512
  val CPP_BTB = true

  // |addr| bits for tag  and |addr| bits for target PC
  // (We should use a Bundle for this, but not sure if Chisel Mem()
  //  implementation supports it yet.)
  val BTB_BITS = conf.xprlen * 2
  val BTB_ADDR_BITS = log2Up(BTB_ENTRIES)
  def get_btb_addr_bits(word: Bits) = word(BTB_ADDR_BITS+1, 2)
  
  // BTB memory itself.
  val btb = Mem(Bits(width=BTB_BITS), BTB_ENTRIES)
  val btb_write_addr  = UInt(width=BTB_ADDR_BITS)
  val btb_write_en    = Bool()
  val btb_write_data  = UInt(width=BTB_BITS)
  val btb_read_addr   = UInt(width=BTB_ADDR_BITS)
  btb_write_addr  := UInt(0)
  btb_write_en    := Bool(false)
  btb_write_data  := Bits(0)
  btb_read_addr   := UInt(0)
  when(btb_write_en) {
    btb(btb_write_addr) := btb_write_data
  }
  val btb_read_out = btb(btb_read_addr)
  val btb_read_data = Bits(width=BTB_BITS)
  
  // Read out BTB entry for current fetch PC
  val if_entry         = btb_read_data
  val if_entry_pc      = if_entry(BTB_BITS-1, BTB_BITS-conf.xprlen)
  val if_entry_target  = if_entry(conf.xprlen-1, 0)
  val if_entry_addr    = get_btb_addr_bits(io.if_pc_reg)

  // Update BTB when mem stage confirms a branch or jump
  val taken = io.exe_pc_sel != PC_PLUS4
  val is_brjmp = io.exe_reg_ctrl_br_type != BR_N
  val tag = Mux(is_brjmp && taken, io.exe_reg_pc, UInt(0))
  val new_entry = Cat(tag, io.exe_pc_next)          
  
  val update_en = is_brjmp || io.exe_reg_pred_taken
  val update_addr = get_btb_addr_bits(io.exe_reg_pc)
  val update_data = new_entry

  // Outputs to dpath and ctrl
  if (CPP_BTB) {
    // Leave dangling, control from emulator.
  } else if(BTB_ENTRIES > 1) {
    io.if_pred_target   := if_entry_target
    io.if_pred_taken    := if_entry_pc === io.if_pc_reg
  } else {
    io.if_pred_target   := UInt(0)
    io.if_pred_taken    := Bool(false)
  }
    
  
  
  // Take care of mem reset
  val reset_mode     = Reg(init=Bool(true))
  val reset_started  = Reg(init=Bool(false))
  val reset_addr     = Reg(UInt(width=BTB_ADDR_BITS))
  when(reset_mode && !reset_started) {
    reset_addr     := UInt(0, BTB_ADDR_BITS)
    reset_started  := Bool(true)
  }.elsewhen(reset_mode && reset_started) {
    when(reset_addr != UInt(BTB_ENTRIES-1, BTB_ADDR_BITS)) {
      btb_write_data  := UInt(0, BTB_BITS)
      btb_write_addr  := reset_addr
      btb_write_en    := Bool(true)
      reset_addr      := reset_addr+UInt(1)
    }.otherwise {
      reset_mode      := Bool(false)
      reset_started   := Bool(false)
      btb_write_en    := Bool(true)
      btb_write_addr  := reset_addr
      btb_write_data  := UInt(0, BTB_BITS)
    }
  }.otherwise {
    btb_write_addr  := update_addr
    btb_write_en    := update_en
    btb_write_data  := update_data
    btb_read_addr   := if_entry_addr
  }
  btb_read_data := Mux(reset_mode, UInt(0, BTB_BITS), btb_read_out)

}


}
