//**************************************************************************
// RISCV Processor 5-Stage Datapath
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2012 Jan 13

package Sodor
{

import Chisel._
import Node._

import Constants._
import Common._

class DatToCtlIo extends Bundle() 
{
   val dec_inst    = Bits(OUTPUT, 32)
   val exe_br_eq   = Bool(OUTPUT)
   val exe_br_lt   = Bool(OUTPUT)
   val exe_br_ltu  = Bool(OUTPUT)
   val exe_br_type = UInt(OUTPUT,  4)
   
   val mem_ctrl_dmem_val = Bool(OUTPUT)
}

class DpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{  
   val host  = new HTIFIO()
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
   val ctl  = new CtlToDatIo().flip()
   val dat  = new DatToCtlIo()
}

class DatPath(implicit conf: SodorConfiguration) extends Module
{
   val io = new DpathIo()
   
   //**********************************
   // Pipeline State Registers
   
   // Instruction Fetch State
   val if_reg_pc             = RegReset(UInt(START_ADDR, conf.xprlen))
   
   // Instruction Decode State
   val dec_reg_inst          = RegReset(BUBBLE)
   val dec_reg_pc            = RegReset(UInt(0, conf.xprlen))
   
   // Execute State
   val exe_reg_inst          = RegReset(BUBBLE)
//   val exe_reg_pc            = Reg(){ UInt() }
   val exe_reg_pc            = RegReset(UInt(0, conf.xprlen))
   val exe_reg_wbaddr        = Reg(UInt())
   val exe_reg_rs1_addr      = Reg(UInt())
   val exe_reg_rs2_addr      = Reg(UInt())
   val exe_reg_op1_data      = Reg(Bits())
   val exe_reg_op2_data      = Reg(Bits())
   val exe_reg_rs2_data      = Reg(Bits())
   val exe_reg_ctrl_br_type  = RegReset(BR_N)
   val exe_reg_ctrl_op2_sel  = Reg(UInt())
   val exe_reg_ctrl_alu_fun  = Reg(UInt())
   val exe_reg_ctrl_wb_sel   = Reg(UInt())
   val exe_reg_ctrl_rf_wen   = RegReset(Bool(false))
   val exe_reg_ctrl_mem_val  = RegReset(Bool(false))
   val exe_reg_ctrl_mem_fcn  = RegReset(M_X)
   val exe_reg_ctrl_pcr_fcn  = RegReset(PCR_N)
   
   // Memory State
   val mem_reg_pc            = Reg(UInt())
   val mem_reg_alu_out       = Reg(Bits())
   val mem_reg_wbaddr        = Reg(UInt())
   val mem_reg_rs1_addr      = Reg(UInt())
   val mem_reg_rs2_addr      = Reg(UInt())
   val mem_reg_op1_data      = Reg(Bits())
   val mem_reg_rs2_data      = Reg(Bits())
   val mem_reg_ctrl_rf_wen   = RegReset(Bool(false))
   val mem_reg_ctrl_mem_val  = RegReset(Bool(false))
   val mem_reg_ctrl_mem_fcn  = RegReset(M_X)
   val mem_reg_ctrl_wb_sel   = Reg(UInt())
   val mem_reg_ctrl_pcr_fcn  = RegReset(PCR_N)

   // Writeback State
   val wb_reg_wbaddr         = Reg(UInt())
   val wb_reg_wbdata         = Reg(Bits(width = conf.xprlen))
   val wb_reg_ctrl_rf_wen    = RegReset(Bool(false))


   //**********************************
   // Instruction Fetch Stage
   val if_pc_next          = UInt()
   val exe_brjmp_target    = UInt()
   val exe_jump_reg_target = UInt()
 
   when (!io.ctl.dec_stall && !io.ctl.full_stall) 
   {
      if_reg_pc := if_pc_next
   }

   val if_pc_plus4 = (if_reg_pc + UInt(4, conf.xprlen))               

   if_pc_next := MuxCase(if_pc_plus4, Array(
                  (io.ctl.exe_pc_sel === PC_PLUS4) -> if_pc_plus4,
                  (io.ctl.exe_pc_sel === PC_BRJMP) -> exe_brjmp_target,
                  (io.ctl.exe_pc_sel === PC_JALR)  -> exe_jump_reg_target
                  ))
   
   // Instruction Memory
   io.imem.req.bits.addr := if_reg_pc
   val if_inst = io.imem.resp.bits.data
   
   when (!io.ctl.dec_stall && !io.ctl.full_stall)
   {
      when (io.ctl.if_kill)
      {
         dec_reg_inst := BUBBLE
      }
      .otherwise
      {
         dec_reg_inst := if_inst
      }

      dec_reg_pc := if_reg_pc
   }

   
   //**********************************
   // Decode Stage
   val dec_rs1_addr = dec_reg_inst(26, 22).toUInt
   val dec_rs2_addr = dec_reg_inst(21, 17).toUInt
   val dec_wbaddr  = Mux(io.ctl.wa_sel, dec_reg_inst(31, 27).toUInt, RA)
   
 
   // Register File
   val regfile = Module(new RegisterFile())
      regfile.io.rs1_addr := dec_rs1_addr
      regfile.io.rs2_addr := dec_rs2_addr
      val rf_rs1_data = regfile.io.rs1_data
      val rf_rs2_data = regfile.io.rs2_data
      regfile.io.waddr := wb_reg_wbaddr
      regfile.io.wdata := wb_reg_wbdata
      regfile.io.wen   := wb_reg_ctrl_rf_wen

 
   // immediates
   val imm_btype = Cat(dec_reg_inst(31,27), dec_reg_inst(16,10))
   val imm_itype = dec_reg_inst(21,10)
   val imm_ltype = dec_reg_inst(26,7)
   val imm_jtype = dec_reg_inst(31,7)

   // sign-extend immediates
   val imm_itype_sext = Cat(Fill(imm_itype(11), 20), imm_itype)
   val imm_btype_sext = Cat(Fill(imm_btype(11), 20), imm_btype)
   val imm_ltype_sext = Cat(Fill(imm_ltype(19), 12), imm_ltype)
   val imm_jtype_sext = Cat(Fill(imm_jtype(24),  7), imm_jtype)

   // Operand 2 Mux   
   val dec_alu_op2 = MuxCase(UInt(0), Array(
               (io.ctl.op2_sel === OP2_RS2)   -> rf_rs2_data,
               (io.ctl.op2_sel === OP2_ITYPE) -> imm_itype_sext,
               (io.ctl.op2_sel === OP2_BTYPE) -> imm_btype_sext,
               (io.ctl.op2_sel === OP2_LTYPE) -> Cat(imm_ltype_sext(19,0), Fill(UInt(0),12)),
               (io.ctl.op2_sel === OP2_JTYPE) -> imm_jtype_sext
               )).toUInt



   // Bypass Muxes
   val exe_alu_out  = UInt(width = conf.xprlen)
   val mem_wbdata   = Bits(width = conf.xprlen)

   val dec_op1_data = Bits(width = conf.xprlen)
   val dec_op2_data = Bits(width = conf.xprlen)
   val dec_rs2_data = Bits(width = conf.xprlen)
   
   if (USE_FULL_BYPASSING)
   {
      // roll the OP1 mux into the bypass mux logic
      dec_op1_data := MuxCase(rf_rs1_data, Array(
                           ((io.ctl.op1_sel === OP1_PC)) -> dec_reg_pc,
                           ((exe_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr != UInt(0)) && exe_reg_ctrl_rf_wen) -> exe_alu_out,
                           ((mem_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr != UInt(0)) && mem_reg_ctrl_rf_wen) -> mem_wbdata,
                           ((wb_reg_wbaddr  === dec_rs1_addr) && (dec_rs1_addr != UInt(0)) &&  wb_reg_ctrl_rf_wen) -> wb_reg_wbdata
                           ))
                               
      dec_op2_data := MuxCase(dec_alu_op2, Array(
                           ((exe_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr != UInt(0)) && exe_reg_ctrl_rf_wen && (io.ctl.op2_sel === OP2_RS2)) -> exe_alu_out,
                           ((mem_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr != UInt(0)) && mem_reg_ctrl_rf_wen && (io.ctl.op2_sel === OP2_RS2)) -> mem_wbdata,
                           ((wb_reg_wbaddr  === dec_rs2_addr) && (dec_rs2_addr != UInt(0)) &&  wb_reg_ctrl_rf_wen && (io.ctl.op2_sel === OP2_RS2)) -> wb_reg_wbdata
                           ))
   
      dec_rs2_data := MuxCase(rf_rs2_data, Array(
                           ((exe_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr != UInt(0)) && exe_reg_ctrl_rf_wen) -> exe_alu_out,
                           ((mem_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr != UInt(0)) && mem_reg_ctrl_rf_wen) -> mem_wbdata,
                           ((wb_reg_wbaddr  === dec_rs2_addr) && (dec_rs2_addr != UInt(0)) &&  wb_reg_ctrl_rf_wen) -> wb_reg_wbdata
                           ))
   }
   else
   {
      //rely only on control interlocking to resolve hazards
      dec_op1_data := Mux(io.ctl.op1_sel === OP1_PC, dec_reg_pc, rf_rs1_data)
      dec_rs2_data := rf_rs2_data
      dec_op2_data := dec_alu_op2
   }
   
   
   when(!io.ctl.dec_stall && !io.ctl.full_stall)
   {
      // no stalling...
      exe_reg_pc            := dec_reg_pc
      exe_reg_rs1_addr      := dec_rs1_addr
      exe_reg_rs2_addr      := dec_rs2_addr
      exe_reg_op1_data      := dec_op1_data
      exe_reg_op2_data      := dec_op2_data
      exe_reg_rs2_data      := dec_rs2_data
      exe_reg_ctrl_op2_sel  := io.ctl.op2_sel  
      exe_reg_ctrl_alu_fun  := io.ctl.alu_fun  
      exe_reg_ctrl_wb_sel   := io.ctl.wb_sel   

      when (io.ctl.dec_kill)
      {
         exe_reg_inst          := BUBBLE
         exe_reg_wbaddr        := UInt(0) 
         exe_reg_ctrl_rf_wen   := Bool(false)
         exe_reg_ctrl_mem_val  := Bool(false)
         exe_reg_ctrl_mem_fcn  := M_X
         exe_reg_ctrl_pcr_fcn  := PCR_N
         exe_reg_ctrl_br_type  := BR_N
      }
      .otherwise
      {
         exe_reg_inst          := dec_reg_inst
         exe_reg_wbaddr        := dec_wbaddr
         exe_reg_ctrl_rf_wen   := io.ctl.rf_wen
         exe_reg_ctrl_mem_val  := io.ctl.mem_val  
         exe_reg_ctrl_mem_fcn  := io.ctl.mem_fcn   
         exe_reg_ctrl_pcr_fcn  := io.ctl.pcr_fcn
         exe_reg_ctrl_br_type  := io.ctl.br_type
      }
   }
   .elsewhen (io.ctl.dec_stall && !io.ctl.full_stall)
   {
      // (kill exe stage)
      // insert NOP (bubble) into Execute stage on front-end stall (e.g., hazard clearing)
      exe_reg_inst          := BUBBLE
      exe_reg_wbaddr        := UInt(0) 
      exe_reg_ctrl_rf_wen   := Bool(false)
      exe_reg_ctrl_mem_val  := Bool(false)
      exe_reg_ctrl_mem_fcn  := M_X
      exe_reg_ctrl_pcr_fcn  := PCR_N
      exe_reg_ctrl_br_type  := BR_N
   }


   //**********************************
   // Execute Stage

   val exe_alu_op1 = exe_reg_op1_data.toUInt
   val exe_alu_op2 = exe_reg_op2_data.toUInt

   // ALU
   val alu_shamt     = exe_alu_op2(4,0).toUInt
   val exe_adder_out = (exe_alu_op1 + exe_alu_op2)(conf.xprlen-1,0)
   
//   exe_alu_out := MuxCase(UInt(0), Array(     
   //only for debug purposes right now until debug() works
   exe_alu_out := MuxCase(exe_reg_inst.toUInt, Array( 
                  (exe_reg_ctrl_alu_fun === ALU_ADD)  -> exe_adder_out, 
                  (exe_reg_ctrl_alu_fun === ALU_SUB)  -> (exe_alu_op1 - exe_alu_op2).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_AND)  -> (exe_alu_op1 & exe_alu_op2).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_OR)   -> (exe_alu_op1 | exe_alu_op2).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_XOR)  -> (exe_alu_op1 ^ exe_alu_op2).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_SLT)  -> (exe_alu_op1.toSInt < exe_alu_op2.toSInt).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_SLTU) -> (exe_alu_op1 < exe_alu_op2).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_SLL)  -> ((exe_alu_op1 << alu_shamt)(conf.xprlen-1, 0)).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_SRA)  -> (exe_alu_op1.toSInt >> alu_shamt).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_SRL)  -> (exe_alu_op1 >> alu_shamt).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_COPY_2)-> exe_alu_op2
                  ))

   // Branch/Jump Target Calculation
   val brjmp_offset    = Cat(exe_reg_op2_data(conf.xprlen-1,0), UInt(0,1)).toUInt
   exe_brjmp_target    := exe_reg_pc + brjmp_offset
   exe_jump_reg_target := exe_adder_out

   val exe_pc_plus4    = (exe_reg_pc + UInt(4))(conf.xprlen-1,0)
      

   when (!io.ctl.full_stall)
   {
      mem_reg_pc            := exe_reg_pc
      mem_reg_alu_out       := Mux((exe_reg_ctrl_wb_sel === WB_PC4), exe_pc_plus4, exe_alu_out)
      mem_reg_wbaddr        := exe_reg_wbaddr
      mem_reg_rs1_addr      := exe_reg_rs1_addr
      mem_reg_rs2_addr      := exe_reg_rs2_addr
      mem_reg_op1_data      := exe_reg_op1_data
      mem_reg_rs2_data      := exe_reg_rs2_data
      mem_reg_ctrl_rf_wen   := exe_reg_ctrl_rf_wen
      mem_reg_ctrl_mem_val  := exe_reg_ctrl_mem_val
      mem_reg_ctrl_mem_fcn  := exe_reg_ctrl_mem_fcn
      mem_reg_ctrl_pcr_fcn  := exe_reg_ctrl_pcr_fcn
      mem_reg_ctrl_wb_sel   := exe_reg_ctrl_wb_sel
   }

   
   //**********************************
   // Memory Stage
   
   // Co-processor Registers
   val pcr = Module(new PCR())
   pcr.io.host <> io.host
   pcr.io.r.addr := mem_reg_rs1_addr
   pcr.io.r.en   := mem_reg_ctrl_pcr_fcn != PCR_N
   val pcr_out = pcr.io.r.data
   pcr.io.w.addr := mem_reg_rs1_addr
   pcr.io.w.en   := mem_reg_ctrl_pcr_fcn === PCR_T
   pcr.io.w.data := mem_reg_rs2_data

 
 
   // WB Mux
   mem_wbdata := MuxCase(mem_reg_alu_out, Array(
                  (mem_reg_ctrl_wb_sel === WB_ALU) -> mem_reg_alu_out,
                  (mem_reg_ctrl_wb_sel === WB_PC4) -> mem_reg_alu_out,
                  (mem_reg_ctrl_wb_sel === WB_MEM) -> io.dmem.resp.bits.data, 
                  (mem_reg_ctrl_wb_sel === WB_PCR) -> pcr_out
                  )).toSInt()


   //**********************************
   // Writeback Stage

   when (!io.ctl.full_stall)
   {
      wb_reg_wbaddr        := mem_reg_wbaddr
      wb_reg_wbdata        := mem_wbdata
      wb_reg_ctrl_rf_wen   := mem_reg_ctrl_rf_wen
   }
   .otherwise
   {
      wb_reg_ctrl_rf_wen   := Bool(false)
   }
 

   
   //**********************************
   // External Signals
   
   // datapath to controlpath outputs
   io.dat.dec_inst   := dec_reg_inst
   io.dat.exe_br_eq  := (exe_reg_op1_data === exe_reg_rs2_data)
   io.dat.exe_br_lt  := (exe_reg_op1_data.toSInt < exe_reg_rs2_data.toSInt) 
   io.dat.exe_br_ltu := (exe_reg_op1_data.toUInt < exe_reg_rs2_data.toUInt)
   io.dat.exe_br_type:= exe_reg_ctrl_br_type

   io.dat.mem_ctrl_dmem_val := mem_reg_ctrl_mem_val
   
   
   // datapath to data memory outputs
   io.dmem.req.valid     := mem_reg_ctrl_mem_val
   io.dmem.req.bits.addr := mem_reg_alu_out.toUInt
   io.dmem.req.bits.fcn  := mem_reg_ctrl_mem_fcn
   io.dmem.req.bits.data := mem_reg_rs2_data
   io.dmem.req.bits.typ  := MT_WU //for now only support word accesses
 
   
   // Time Stamp Counter & Retired Instruction Counter 
   val tsc_reg = RegReset(UInt(0, conf.xprlen))
   tsc_reg := tsc_reg + UInt(1)

   val irt_reg = RegReset(UInt(0, conf.xprlen))
   when (!io.ctl.full_stall && !io.ctl.dec_stall) { irt_reg := irt_reg + UInt(1) }
        
                                     
   // Printout
   printf("Cyc= %d (0x%x, 0x%x, 0x%x, 0x%x, 0x%x) [%s, %s, %s, %s, %s] %s %s ExeInst: %s\n"
      , tsc_reg(31,0)
      , if_reg_pc
      , dec_reg_pc
      , exe_reg_pc
      , RegUpdate(exe_reg_pc)
      , RegUpdate(RegUpdate(exe_reg_pc))
      , Disassemble(if_inst, true)
      , Disassemble(dec_reg_inst, true)
      , Disassemble(exe_reg_inst, true)
      , RegUpdate(Disassemble(exe_reg_inst, true))
      , RegUpdate(RegUpdate(Disassemble(exe_reg_inst, true)))
      , Mux(io.ctl.full_stall, Str("FREEZE"), 
        Mux(io.ctl.dec_stall, Str("STALL "), Str(" ")))
      , Mux(io.ctl.exe_pc_sel === UInt(1), Str("BR"),
        Mux(io.ctl.exe_pc_sel === UInt(2), Str("J "),
        Mux(io.ctl.exe_pc_sel === UInt(3), Str("JR"),
        Mux(io.ctl.exe_pc_sel === UInt(4), Str("EX"),
        Mux(io.ctl.exe_pc_sel === UInt(0), Str("  "), Str("??"))))))
      , Disassemble(exe_reg_inst)
      )
 
}

 
}
