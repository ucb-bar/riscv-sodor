//**************************************************************************
// RISCV U-Coded Processor Data Path
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 May 28

package Sodor
{

import Chisel._
import Node._

import Constants._
import Common._
import Common.Constants._


class DatToCtlIo extends Bundle() 
{
   val inst     = UInt(OUTPUT, 32)
   val alu_zero = Bool(OUTPUT)

   val csr_eret = Bool(OUTPUT)
   val csr_xcpt = Bool(OUTPUT)
}


class DpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val host = new HTIFIO()
   val mem  = new MemPortIo(conf.xprlen)
   val ctl  = new CtlToDatIo().flip()
   val dat  = new DatToCtlIo()
}  


class DatPath(implicit conf: SodorConfiguration) extends Module
{
   val io = new DpathIo()


   // forward declarations
   val imm       = Bits(width = conf.xprlen)
   val alu       = Bits(width = conf.xprlen)
   val reg_rdata = Bits(width = conf.xprlen)
   val csr_rdata = Bits(width = conf.xprlen)
   val exception_target = UInt()

   // The Bus 
   // (this is a bus-based RISCV implementation, so all data movement goes
   // across this wire)
   val bus = MuxCase(Bits(0,conf.xprlen), Array(
               (io.ctl.en_imm)                  -> imm(conf.xprlen-1,0),
               (io.ctl.en_alu)                  -> alu(conf.xprlen-1,0), 
               (io.ctl.en_reg & ~io.ctl.reg_wr & 
                 (io.ctl.reg_sel != RS_CR))     -> reg_rdata(conf.xprlen-1,0),
               (io.ctl.en_mem & ~io.ctl.mem_wr) -> io.mem.resp.bits.data(31,0),
               (io.ctl.en_reg & ~io.ctl.reg_wr & 
                  (io.ctl.reg_sel === RS_CR))   -> csr_rdata
             ))
 
   

   // IR Register
   val ir    = Reg(init=Bits(0,32))
   when (io.ctl.ld_ir) { ir := bus }
   io.dat.inst := ir
    
   // A Register
   val reg_a = Reg(init=Bits(0xaaaa,conf.xprlen))
   when (io.ctl.ld_a) { reg_a := bus }
     
   // B Register
   val reg_b = Reg(init=Bits(0xbbbb,conf.xprlen))
   when (io.ctl.ld_b) { reg_b := bus }
    
   // MA Register
   val reg_ma  = Reg(init=Bits(0xeeee,conf.xprlen))
   when (io.ctl.ld_ma) { reg_ma := bus }

   // IR Immediate
   imm := MuxCase(Bits(0), Array(
             (io.ctl.is_sel === IS_I)  -> Cat(Fill(ir(31),(20)),ir(31,20)), 
             (io.ctl.is_sel === IS_S)  -> Cat(Fill(ir(31),(20)),ir(31,25),ir(11,7)),
             (io.ctl.is_sel === IS_U)  -> Cat(ir(31,12),SInt(0,12)),
             (io.ctl.is_sel === IS_B)  -> Cat(Fill(ir(31),(20)),ir(7),ir(30,25),ir(11,8),UInt(0,1)),
             (io.ctl.is_sel === IS_J)  -> Cat(Fill(ir(31),(20)),ir(19,12),ir(20),ir(30,21),UInt(0,1)),
             (io.ctl.is_sel === IS_Z)  -> Cat(UInt(0,27), ir(19,15))
           ))

     

   
   // Register File (Single Port)
   // also holds the PC register
   val rs1 = ir(RS1_MSB, RS1_LSB)
   val rs2 = ir(RS2_MSB, RS2_LSB)
   val rd  = ir(RD_MSB,  RD_LSB)

   val reg_addr  = MuxCase(UInt(0), Array(
                     (io.ctl.reg_sel === RS_PC)  -> PC_IDX,
                     (io.ctl.reg_sel === RS_RD)  -> rd,
                     (io.ctl.reg_sel === RS_RS1) -> rs1,
                     (io.ctl.reg_sel === RS_RS2) -> rs2,
                     (io.ctl.reg_sel === RS_X0)  -> X0,
                     (io.ctl.reg_sel === RS_CA)  -> X0,
                     (io.ctl.reg_sel === RS_CR)  -> X0
                   ))
 
   //note: I could be far more clever and save myself on wasted registers here...
   //32 x-registers, 1 pc-register
   val regfile = Vec.fill(33){ Reg(init=Bits(0, conf.xprlen)) }

   when (io.ctl.en_reg & io.ctl.reg_wr & reg_addr != UInt(0))
   {
      regfile(reg_addr) := bus
   }
  
   // This is a hack to make it look like the CSRFile is part of the regfile
   reg_rdata :=  MuxCase(regfile(reg_addr), Array(
                    (io.ctl.reg_sel === RS_CR) -> csr_rdata,
                    (reg_addr === UInt(0))     -> Bits(0, conf.xprlen)))
                    

   // CSR addr Register
   val csr_addr = Reg(init=Bits(0, 12))
   when(io.ctl.reg_wr & (io.ctl.reg_sel === RS_CA)) {
     csr_addr := bus
   }

   val csr_wdata = Reg(init=Bits(0, conf.xprlen))
   when(io.ctl.reg_wr & (io.ctl.reg_sel === RS_CR)) {
     csr_wdata := bus
   }
   
   // Control Status Registers
   val csr = Module(new CSRFile())
   csr.io.host <> io.host
   csr.io.rw.addr  := csr_addr
   csr.io.rw.wdata := csr_wdata
   csr.io.rw.cmd   := io.ctl.csr_cmd
   csr_rdata       := csr.io.rw.rdata 

   csr.io.retire    := io.ctl.upc_is_fetch

   // for now, the ucode does NOT support exceptions
   csr.io.exception := Bool(false)  
   csr.io.cause     := UInt(Common.Causes.illegal_instruction)
   csr.io.pc        := regfile(PC_IDX) - UInt(4) 
   exception_target := csr.io.evec

   io.dat.csr_eret := csr.io.eret
   io.dat.csr_xcpt := csr.io.csr_xcpt
   //io.dat.csr_interrupt := csr.io.interrupt
   //io.dat.csr_interrupt_cause := csr.io.interrupt_cause


   // Add your own uarch counters here!
   csr.io.uarch_counters.foreach(_ := Bool(false))

   // ALU
   val alu_shamt = reg_b(4,0).toUInt

   alu := MuxCase(Bits(0), Array[(Bool, UInt)](
              (io.ctl.alu_op === ALU_COPY_A)  ->  reg_a,
              (io.ctl.alu_op === ALU_COPY_B)  ->  reg_b,
              (io.ctl.alu_op === ALU_INC_A_1) ->  (reg_a  +  UInt(1)),
              (io.ctl.alu_op === ALU_DEC_A_1) ->  (reg_a  -  UInt(1)),
              (io.ctl.alu_op === ALU_INC_A_4) ->  (reg_a  +  UInt(4)),
              (io.ctl.alu_op === ALU_DEC_A_4) ->  (reg_a  -  UInt(4)),
              (io.ctl.alu_op === ALU_ADD)     ->  (reg_a  +  reg_b),
              (io.ctl.alu_op === ALU_SUB)     ->  (reg_a  -  reg_b),
              (io.ctl.alu_op === ALU_SLL)     -> ((reg_a << alu_shamt)(conf.xprlen-1,0)),
              (io.ctl.alu_op === ALU_SRL)     ->  (reg_a >> alu_shamt),
              (io.ctl.alu_op === ALU_SRA)     ->  (reg_a.toSInt >> alu_shamt).toUInt,
              (io.ctl.alu_op === ALU_AND)     ->  (reg_a & reg_b),
              (io.ctl.alu_op === ALU_OR)      ->  (reg_a | reg_b),
              (io.ctl.alu_op === ALU_XOR)     ->  (reg_a ^ reg_b),
              (io.ctl.alu_op === ALU_SLT)     ->  (reg_a.toSInt < reg_b.toSInt).toUInt,
              (io.ctl.alu_op === ALU_SLTU)    ->  (reg_a < reg_b),
              (io.ctl.alu_op === ALU_INIT_PC) ->  UInt(START_ADDR),
              (io.ctl.alu_op === ALU_MASK_12) ->  (reg_a & ~UInt((1<<12)-1, conf.xprlen)),
              (io.ctl.alu_op === ALU_EVEC)    ->  exception_target
            ))


  
   // Output Signals to the Control Path
   io.dat.alu_zero := (alu === UInt(0))
   
   // Output Signals to the Memory
   io.mem.req.bits.addr := reg_ma.toUInt
   io.mem.req.bits.data := bus
                              
   // Retired Instruction Counter 
   val irt_reg = Reg(init=UInt(0, conf.xprlen))
   when (io.ctl.upc_is_fetch) { irt_reg := irt_reg + UInt(1) }

   // Printout
   printf("%sCyc= %d (MA=0x%x) %s RegAddr=%d Bus=0x%x A=0x%x B=0x%x PCReg=( 0x%x ) UPC=%d InstReg=[ 0x%x : DASM(%x) ]\n"
      , Mux(io.ctl.upc_is_fetch, Str("\n  "), Str(" "))
      , csr.io.time(31,0)
      , reg_ma
      , Mux(io.ctl.en_mem, Str("EN"), Str("  "))
      , reg_addr
      , bus
      , reg_a
      , reg_b
      , regfile(PC_IDX) // this is the PC register
      , io.ctl.upc
      , ir
      , ir
      );
  
}

}

