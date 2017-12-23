//**************************************************************************
// RISCV U-Coded Processor Data Path
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 May 28

package Sodor
{

import chisel3._
import chisel3.util._
import config._
import Constants._
import Common._
import Common.Constants._


class DatToCtlIo extends Bundle() 
{
   val inst     = Output(UInt(32.W))
   val alu_zero = Output(Bool())
   val csr_eret = Output(Bool())
   override def cloneType = { new DatToCtlIo().asInstanceOf[this.type] }
}


class DpathIo(implicit p: Parameters) extends Bundle() 
{
   val ddpath = Flipped(new DebugDPath())
   val mem  = new MemPortIo(p(xprlen))
   val ctl  = Flipped(new CtlToDatIo())
   val dat  = new DatToCtlIo()
}  


class DatPath(implicit p: Parameters) extends Module
{
   val io = IO(new DpathIo())
   //Initialize IO
   io.mem.req.bits := new MemReq(p(xprlen)).fromBits(0.U)
   io.mem.req.valid := false.B
   io.mem.resp.ready := true.B
   io.ddpath.rdata := 0.U
   val xlen = p(xprlen)

   // forward declarations
   val imm       = Wire(UInt(xlen.W))
   val alu       = Wire(UInt(xlen.W))
   val reg_rdata = Wire(UInt(xlen.W))
   val csr_rdata = Wire(UInt(xlen.W))
   val exception_target = Wire(UInt(xlen.W))

   // The Bus 
   // (this is a bus-based RISCV implementation, so all data movement goes
   // across this wire)
   val bus = MuxCase(0.U, Array(
               (io.ctl.en_imm)                  -> imm(xlen-1,0),
               (io.ctl.en_alu)                  -> alu(xlen-1,0), 
               (io.ctl.en_reg & ~io.ctl.reg_wr & 
                 (io.ctl.reg_sel != RS_CR))     -> reg_rdata(xlen-1,0),
               (io.ctl.en_mem & ~io.ctl.mem_wr) -> io.mem.resp.bits.data(xlen-1,0),
               (io.ctl.en_reg & ~io.ctl.reg_wr & 
                  (io.ctl.reg_sel === RS_CR))   -> csr_rdata
             ))
 
   

   // IR Register
   val ir    = Reg(init=0.asUInt(xlen.W))
   when (io.ctl.ld_ir) { ir := bus }
   io.dat.inst := ir
    
   // A Register
   val reg_a = Reg(init="haaaa".asUInt(xlen.W))
   when (io.ctl.ld_a) { reg_a := bus }
     
   // B Register
   val reg_b = Reg(init="hbbbb".asUInt(xlen.W))
   when (io.ctl.ld_b) { reg_b := bus }
    
   // MA Register
   val reg_ma  = Reg(init="heeee".asUInt(xlen.W))
   when (io.ctl.ld_ma) { reg_ma := bus }

   // IR Immediate
   imm := MuxCase(0.U, Array(
             (io.ctl.is_sel === IS_I)  -> Cat(Fill(20,ir(31)),ir(31,20)), 
             (io.ctl.is_sel === IS_S)  -> Cat(Fill(20,ir(31)),ir(31,25),ir(11,7)),
             (io.ctl.is_sel === IS_U)  -> Cat(ir(31,12),SInt(0,12)),
             (io.ctl.is_sel === IS_B)  -> Cat(Fill(20,ir(31)),ir(7),ir(30,25),ir(11,8),0.asUInt(1.W)),
             (io.ctl.is_sel === IS_J)  -> Cat(Fill(20,ir(31)),ir(19,12),ir(20),ir(30,21),0.asUInt(1.W)),
             (io.ctl.is_sel === IS_Z)  -> Cat(0.asUInt(27.W), ir(19,15))
           ))

     

   
   // Register File (Single Port)
   // also holds the PC register
   val rs1 = ir(RS1_MSB, RS1_LSB)
   val rs2 = ir(RS2_MSB, RS2_LSB)
   val rd  = ir(RD_MSB,  RD_LSB)

   val reg_addr  = MuxCase(0.U, Array(
                     (io.ctl.reg_sel === RS_PC)  -> PC_IDX,
                     (io.ctl.reg_sel === RS_RD)  -> rd,
                     (io.ctl.reg_sel === RS_RS1) -> rs1,
                     (io.ctl.reg_sel === RS_RS2) -> rs2
                   ))
 
   //note: I could be far more clever and save myself on wasted registers here...
   //32 x-registers, 1 pc-register
   val regfile = Reg(Vec(33, UInt(32.W)))

   when (io.ctl.en_reg & io.ctl.reg_wr & reg_addr != 0.U)
   {
      regfile(reg_addr) := bus
   }
  
   // This is a hack to make it look like the CSRFile is part of the regfile
   reg_rdata :=  MuxCase(regfile(reg_addr), Array(
                    (io.ctl.reg_sel === RS_CR) -> csr_rdata,
                    (reg_addr === 0.U)     -> 0.asUInt(xlen.W)))
                    
   // CSR addr Register
   val csr_addr = Reg(init=0.asUInt(12.W))
   when(io.ctl.reg_wr & (io.ctl.reg_sel === RS_CA)) {
     csr_addr := bus
   }

   val csr_wdata = Reg(init=0.asUInt(xlen.W))
   when(io.ctl.reg_wr & (io.ctl.reg_sel === RS_CR)) {
     csr_wdata := bus
   }
   
   // Control Status Registers
   val csr = Module(new CSRFile())
   csr.io.decode.csr  := csr_addr
   csr.io.rw.wdata := csr_wdata
   csr.io.rw.cmd   := io.ctl.csr_cmd
   csr_rdata       := csr.io.rw.rdata 
   csr.io.retire    := io.ctl.upc_is_fetch
   // illegal micro-code encountered
   csr.io.illegal := io.ctl.illegal  
   csr.io.pc        := regfile(PC_IDX) - 4.U 
   exception_target := csr.io.evec

   io.dat.csr_eret := csr.io.eret

   // Add your own uarch counters here!
   csr.io.counters.foreach(_.inc := false.B)

   // ALU
   val alu_shamt = reg_b(4,0).toUInt

   alu := MuxCase(0.U, Array[(Bool, UInt)](
              (io.ctl.alu_op === ALU_COPY_A)  ->  reg_a,
              (io.ctl.alu_op === ALU_COPY_B)  ->  reg_b,
              (io.ctl.alu_op === ALU_INC_A_1) ->  (reg_a  +  1.U),
              (io.ctl.alu_op === ALU_DEC_A_1) ->  (reg_a  -  1.U),
              (io.ctl.alu_op === ALU_INC_A_4) ->  (reg_a  +  4.U),
              (io.ctl.alu_op === ALU_DEC_A_4) ->  (reg_a  -  4.U),
              (io.ctl.alu_op === ALU_ADD)     ->  (reg_a  +  reg_b),
              (io.ctl.alu_op === ALU_SUB)     ->  (reg_a  -  reg_b),
              (io.ctl.alu_op === ALU_SLL)     -> ((reg_a << alu_shamt)(xlen-1,0)),
              (io.ctl.alu_op === ALU_SRL)     ->  (reg_a >> alu_shamt),
              (io.ctl.alu_op === ALU_SRA)     ->  (reg_a.toSInt >> alu_shamt).toUInt,
              (io.ctl.alu_op === ALU_AND)     ->  (reg_a & reg_b),
              (io.ctl.alu_op === ALU_OR)      ->  (reg_a | reg_b),
              (io.ctl.alu_op === ALU_XOR)     ->  (reg_a ^ reg_b),
              (io.ctl.alu_op === ALU_SLT)     ->  (reg_a.toSInt < reg_b.toSInt).toUInt,
              (io.ctl.alu_op === ALU_SLTU)    ->  (reg_a < reg_b),
              (io.ctl.alu_op === ALU_INIT_PC) ->  START_ADDR,
              (io.ctl.alu_op === ALU_MASK_12) ->  (reg_a & ~((1<<12)-1).asUInt(xlen.W)),
              (io.ctl.alu_op === ALU_EVEC)    ->  exception_target
            ))
   // Output Signals to the Control Path
   io.dat.alu_zero := (alu === 0.U)
   
   // Output Signals to the Memory
   io.mem.req.bits.addr := reg_ma.toUInt
   io.mem.req.bits.data := bus
   // Retired Instruction Counter 
   val irt_reg = Reg(init=0.asUInt(xlen.W))
   when (io.ctl.upc_is_fetch) { irt_reg := irt_reg + 1.U }

   // Printout
   printf("%cCyc= %d (MA=0x%x) %d %c %c RegAddr=%d Bus=0x%x A=0x%x B=0x%x PCReg=( 0x%x ) UPC=%d InstReg=[ 0x%x : DASM(%x) ]\n"
      , Mux(io.ctl.upc_is_fetch, Str("F"), Str(" "))
      , csr.io.time(31,0)
      , reg_ma
      , io.ctl.reg_sel
      , Mux(io.ctl.en_mem, Str("E"), Str(" ")) 
      , Mux(io.ctl.illegal, Str("X"), Str(" ")) 
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

