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
  val if_reg_pc           = UInt(INPUT, conf.xprlen)
  
  val dec_pred_taken  = Bool(OUTPUT)
  val dec_pred_target = UInt(OUTPUT, conf.xprlen)
  
  val exe_reg_pc            = UInt(INPUT, conf.xprlen)
  val exe_pc_next           = UInt(INPUT, conf.xprlen)
  val exe_reg_ctrl_br_type  = UInt(INPUT, width=BR_N.width)
  val exe_pc_sel            = UInt(INPUT, width=PC_PLUS4.width)
  val exe_reg_pred_taken    = Bool(INPUT)
}

class BTB(implicit conf: SodorConfiguration) extends Module {
  val io = new BTBIo()

  val BTB_ENTRIES = 1

  // 1 valid bit and |addr| bits for target PC
  // (We should use a Bundle for this, but not sure if Chisel Mem()
  //  syntax supports it yet.)
  val BTB_BITS = conf.xprlen + 1
  val BTB_ADDR_BITS = log2Up(BTB_ENTRIES)
  def get_btb_addr_bits(word: Bits) = word(BTB_ADDR_BITS+1, 2)

  // BTB memory itself.
  val btb = Mem(Bits(width=BTB_BITS), BTB_ENTRIES)
  val btb_write_addr  = UInt(width=BTB_ADDR_BITS)
  val btb_write_en    = Bool()
  val btb_write_data  = UInt(width=BTB_BITS)
  val btb_read_addr   = UInt(width=BTB_ADDR_BITS)
  val btb_read_reg    = Reg(Bits(width=BTB_BITS))
  btb_write_addr  := UInt(0)
  btb_write_en    := Bool(false)
  btb_write_data  := Bits(0)
  btb_read_addr   := UInt(0)
  when(btb_write_en) {
    btb(btb_write_addr) := btb_write_data
  }
  btb_read_reg := btb(btb_read_addr)
  val btb_read_data = Bits(width=BTB_BITS)
  
  
  // Read out BTB entry for current fetch PC
  val entry         = Bits(width=BTB_BITS)
  val entry_valid   = entry(0)
  val entry_target  = entry(BTB_BITS-1, 1)
  val entry_addr    = get_btb_addr_bits(io.if_reg_pc)

  // Update BTB when mem stage confirms a branch or jump
  val taken = io.exe_pc_sel != PC_PLUS4
  val is_brjmp = io.exe_reg_ctrl_br_type != BR_N
  val valid = is_brjmp && taken
  val update_en = is_brjmp || io.exe_reg_pred_taken
  val new_entry = Cat(io.exe_pc_next, valid)          
  val update_addr = get_btb_addr_bits(io.exe_reg_pc)
  entry := Mux(entry_addr != update_addr, btb_read_data, new_entry)

  // Outputs to dpath and ctrl
  io.dec_pred_target   := entry_target
  io.dec_pred_taken    := entry_valid

  
  
  // Take care of mem reset
  val reset_mode     = Reg(init=Bool(true))
  val reset_started  = Reg(init=Bool(false))
  val reset_addr     = Reg(UInt(width=BTB_ADDR_BITS))
  when(reset_mode && !reset_started) {
    reset_addr     := UInt(0, BTB_ADDR_BITS)
    reset_started  := Bool(true)
  }.elsewhen(reset_mode && reset_started) {
    when(reset_addr != UInt((1<<BTB_ADDR_BITS)-1, BTB_ADDR_BITS)) {
      btb_write_data := UInt(0, BTB_BITS)
      btb_write_addr := reset_addr
      btb_write_en   := Bool(true)
      reset_addr      := reset_addr+UInt(1)
    }.otherwise {
      reset_mode    := Bool(false)
      reset_started := Bool(false)
    }
  }.otherwise {
    btb_write_addr  := update_addr
    btb_write_en    := update_en
  }
  btb_read_data := Mux(reset_mode, UInt(0, BTB_BITS), btb_read_reg)

}


}
