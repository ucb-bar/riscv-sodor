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
   val pcr_rdata = Bits(width = conf.xprlen)

   // The Bus 
   // (this is a bus-based RISCV implementation, so all data movement goes
   // across this wire)
   val bus = MuxCase(Bits(0,conf.xprlen), Array(
               (io.ctl.en_imm)                  -> imm(conf.xprlen-1,0),
               (io.ctl.en_alu)                  -> alu(conf.xprlen-1,0), 
               (io.ctl.en_reg & ~io.ctl.reg_wr &
                  io.ctl.reg_sel != RS_CP)      -> reg_rdata(conf.xprlen-1,0),
               (io.ctl.en_mem & ~io.ctl.mem_wr) -> io.mem.resp.bits.data(31,0),
               (io.ctl.en_reg & ~io.ctl.reg_wr & 
                  io.ctl.reg_sel === RS_CP)     -> pcr_rdata
             ))
 
   

   // IR Register
   val ir    = RegReset(Bits(0,32))
   when (io.ctl.ld_ir) { ir := bus }
   io.dat.inst := ir
    
   // A Register
   val reg_a = RegReset(Bits(0xaaaa,conf.xprlen))
   when (io.ctl.ld_a) { reg_a := bus }
     
   // B Register
   val reg_b = RegReset(Bits(0xbbbb,conf.xprlen))
   when (io.ctl.ld_b) { reg_b := bus }
    
   // MA Register
   val reg_ma  = RegReset(Bits(0xeeee,conf.xprlen))
   when (io.ctl.ld_ma) { reg_ma := bus }
 

   // IR Immediate
   imm := MuxCase(Bits(0), Array(
             (io.ctl.is_sel === IS_I)  -> Cat(Fill(ir(21),(20)),ir(21,10)), 
             (io.ctl.is_sel === IS_BS) -> Cat(Fill(ir(31),(20)),ir(31,27),ir(16,10)),
             (io.ctl.is_sel === IS_L)  -> Cat(ir(26,7),SInt(0,12)),
             (io.ctl.is_sel === IS_J)  -> Cat(Fill(ir(31),(6)), ir(31,7),UInt(0,1)),
             (io.ctl.is_sel === IS_BR) -> Cat(Fill(ir(31),(19)),ir(31,27),ir(16,10),UInt(0,1))
           ))

     

   
   // Register File (Single Port)
   // also holds the PC register
   val rs1 = ir(26, 22).toUInt
   val rs2 = ir(21, 17).toUInt
   val rd  = ir(31, 27).toUInt

   val reg_addr  = MuxCase(UInt(0), Array(
                     (io.ctl.reg_sel === RS_PC)  -> PC, 
                     (io.ctl.reg_sel === RS_RD)  -> rd,
                     (io.ctl.reg_sel === RS_RS1) -> rs1,
                     (io.ctl.reg_sel === RS_RS2) -> rs2,
                     (io.ctl.reg_sel === RS_RA)  -> RA,
                     (io.ctl.reg_sel === RS_X0)  -> X0,
                     (io.ctl.reg_sel === RS_CP)  -> (rs1 + UInt(33,7))
                   ))
 
   //note: I could be far more clever and save myself on wasted registers here...
   //32 x-registers, 32 cp-registers, 1 pc-register
//   val regfile = Mem(65){ Bits(resetVal = Bits(0, conf.xprlen)) }
   val regfile = Vec.fill(65){ RegReset(Bits(0, conf.xprlen)) }

   when (io.ctl.en_reg & io.ctl.reg_wr & reg_addr != UInt(0))
   {
      regfile(reg_addr) := bus
   }
   
   reg_rdata :=  Mux((reg_addr === UInt(0)), Bits(0, conf.xprlen), 
                                              regfile(reg_addr))


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
              (io.ctl.alu_op === ALU_SRA)     ->  (reg_a  >> alu_shamt),
              (io.ctl.alu_op === ALU_AND)     ->  (reg_a & reg_b),
              (io.ctl.alu_op === ALU_OR)      ->  (reg_a | reg_b),
              (io.ctl.alu_op === ALU_XOR)     ->  (reg_a ^ reg_b),
              (io.ctl.alu_op === ALU_SLT)     ->  (reg_a < reg_b),
              (io.ctl.alu_op === ALU_SLTU)    ->  (reg_a < reg_b),
              (io.ctl.alu_op === ALU_INIT_PC) ->  UInt(START_ADDR)
            ))


  
   // Output Signals to the Control Path
   io.dat.alu_zero := (alu === UInt(0))
   
   // Output Signals to the Memory
   io.mem.req.bits.addr := reg_ma.toUInt
   io.mem.req.bits.data := bus
                              
    
   // Co-processor Registers
   val pcr = Module(new PCR())
   pcr.io.host <> io.host
   pcr.io.r.addr := rs1
   pcr.io.r.en   := io.ctl.en_reg && !io.ctl.reg_wr && io.ctl.reg_sel === RS_CP
   pcr_rdata     := pcr.io.r.data
   pcr.io.w.addr := rs1
   pcr.io.w.en   := io.ctl.en_reg && io.ctl.reg_wr && io.ctl.reg_sel === RS_CP
   pcr.io.w.data := bus
 
   
   // Time Stamp Counter & Retired Instruction Counter 
   val tsc_reg = RegReset(UInt(0, conf.xprlen))
   tsc_reg := tsc_reg + UInt(1)

   val irt_reg = RegReset(UInt(0, conf.xprlen))
   when (io.ctl.upc_is_fetch) { irt_reg := irt_reg + UInt(1) }
 

   // Printout
   printf("%sCyc= %d PCReg=( 0x%x ) InstReg=[ 0x%x : %s ] UPC=%d (MA=0x%x) %s RegAddr=%d Bus=0x%x A=0x%x B=0x%x\n"
      , Mux(io.ctl.upc_is_fetch, Str("\n  "), Str(" "))
      , tsc_reg(31,0)
      , regfile(32) // this is the PC register
      , ir
      , Disassemble(ir)
      , io.ctl.upc
      , reg_ma
      , Mux(io.ctl.en_mem, Str("EN"), Str("  "))
      , reg_addr
      , bus
      , reg_a
      , reg_b
      );
  
}

}

