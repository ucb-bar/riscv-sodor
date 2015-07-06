//**************************************************************************
// RISCV Processor Control Path
//--------------------------------------------------------------------------
//
// Christopher Celio

package Sodor
{

import Chisel._
import Node._

import Common._
import Common.Instructions._
import Constants._

class CtlToDatIo extends Bundle() 
{
   val stall     = Bool(OUTPUT)
   val pc_sel    = UInt(OUTPUT, 3) 
   val op1_sel   = UInt(OUTPUT, 2) 
   val op2_sel   = UInt(OUTPUT, 2) 
   val alu_fun   = UInt(OUTPUT, 4) 
   val wb_sel    = UInt(OUTPUT, 3) 
   val rf_wen    = Bool(OUTPUT) 
   val csr_cmd   = UInt(OUTPUT, CSR.SZ) 
   val exception = Bool(OUTPUT)
   val exc_cause = UInt(OUTPUT, 32)

   val debug_dmem_val = Bool(OUTPUT)
   val debug_dmem_typ = Bits(OUTPUT, MT_X.getWidth)
}

class CpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
   val dat  = new DatToCtlIo().flip()
   val ctl  = new CtlToDatIo()
   val resetSignal = Bool(INPUT)
   override def clone = { new CpathIo().asInstanceOf[this.type] }
}

                                                                                                                            
class CtlPath(implicit conf: SodorConfiguration) extends Module
{                                                                                                                   
  val io = new CpathIo()                                                                                            
                                                                                                                    
   val csignals =                                                                                                   
      ListLookup(io.dat.inst,                                                                                       
                             List(N, BR_N  , OP1_X  ,  OP2_X  , ALU_X   , WB_X   , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
               Array(       /* val  |  BR  |  op1   |   op2     |  ALU    |  wb  | rf   | mem  | mem  | mask |  csr  */
                            /* inst | type |   sel  |    sel    |   fcn   |  sel | wen  |  en  |  wr  | type |  cmd  */
                  LW      -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_W,  CSR.N),
                  LB      -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_B,  CSR.N),
                  LBU     -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_BU, CSR.N),
                  LH      -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_H,  CSR.N),
                  LHU     -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_HU, CSR.N),
                  SW      -> List(Y, BR_N  , OP1_RS1, OP2_IMS , ALU_ADD ,  WB_X  , REN_0, MEN_1, M_XWR, MT_W,  CSR.N),
                  SB      -> List(Y, BR_N  , OP1_RS1, OP2_IMS , ALU_ADD ,  WB_X  , REN_0, MEN_1, M_XWR, MT_B,  CSR.N),
                  SH      -> List(Y, BR_N  , OP1_RS1, OP2_IMS , ALU_ADD ,  WB_X  , REN_0, MEN_1, M_XWR, MT_H,  CSR.N),
                  
                  AUIPC   -> List(Y, BR_N  , OP1_IMU, OP2_PC  , ALU_ADD ,  WB_ALU, REN_1, MEN_0, M_X ,  MT_X,  CSR.N),
                  LUI     -> List(Y, BR_N  , OP1_IMU, OP2_X   , ALU_COPY1, WB_ALU, REN_1, MEN_0, M_X ,  MT_X,  CSR.N),
                 
                  ADDI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  ANDI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_AND ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  ORI     -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_OR  ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  XORI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_XOR ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SLTI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SLT ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SLTIU   -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SLTU,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SLLI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SLL ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SRAI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SRA ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SRLI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SRL ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                   
                  SLL     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SLL ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  ADD     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_ADD ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SUB     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SUB ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SLT     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SLT ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SLTU    -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SLTU,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  AND     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_AND ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  OR      -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_OR  ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  XOR     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_XOR ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SRA     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SRA ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  SRL     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SRL ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  
                  JAL     -> List(Y, BR_J  , OP1_X  , OP2_X   , ALU_X   ,  WB_PC4, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  JALR    -> List(Y, BR_JR , OP1_RS1, OP2_IMI , ALU_X   ,  WB_PC4, REN_1, MEN_0, M_X  , MT_X,  CSR.N),
                  BEQ     -> List(Y, BR_EQ , OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
                  BNE     -> List(Y, BR_NE , OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
                  BGE     -> List(Y, BR_GE , OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
                  BGEU    -> List(Y, BR_GEU, OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
                  BLT     -> List(Y, BR_LT , OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
                  BLTU    -> List(Y, BR_LTU, OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
  
                  CSRRWI  -> List(Y, BR_N  , OP1_IMZ, OP2_X   , ALU_COPY1, WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.W),
                  CSRRSI  -> List(Y, BR_N  , OP1_IMZ, OP2_X   , ALU_COPY1, WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.S),
                  CSRRCI  -> List(Y, BR_N  , OP1_IMZ, OP2_X   , ALU_COPY1, WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.C),
                  CSRRW   -> List(Y, BR_N  , OP1_RS1, OP2_X   , ALU_COPY1, WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.W),
                  CSRRS   -> List(Y, BR_N  , OP1_RS1, OP2_X   , ALU_COPY1, WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.S),
                  CSRRC   -> List(Y, BR_N  , OP1_RS1, OP2_X   , ALU_COPY1, WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.C),
           
                  SCALL   -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.I),
                  SRET    -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.I),
                  MRTS    -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.I),
                  SBREAK  -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.I),
                  WFI     -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N), // implemented as a NOP

                  FENCE_I -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N),
                  FENCE   -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_1, M_X  , MT_X,  CSR.N)
                  // we are already sequentially consistent, so no need to honor the fence instruction
                  ))

   // Put these control signals into variables
   val (cs_val_inst: Bool) :: cs_br_type         :: cs_op1_sel            :: cs_op2_sel :: cs0 = csignals
   val cs_alu_fun          :: cs_wb_sel          :: (cs_rf_wen: Bool)     ::               cs1 = cs0
   val (cs_mem_en: Bool)   :: cs_mem_fcn         :: cs_msk_sel            :: cs_csr_cmd :: Nil = cs1
                        
   // Branch Logic   
   val ctrl_pc_sel = Mux(io.ctl.exception ||
                         io.dat.csr_eret  ||
                         io.dat.csr_xcpt      ,  PC_EXC,
                     Mux(cs_br_type === BR_N  ,  PC_4,
                     Mux(cs_br_type === BR_NE ,  Mux(!io.dat.br_eq,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_EQ ,  Mux( io.dat.br_eq,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_GE ,  Mux(!io.dat.br_lt,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_GEU,  Mux(!io.dat.br_ltu, PC_BR, PC_4),
                     Mux(cs_br_type === BR_LT ,  Mux( io.dat.br_lt,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_LTU,  Mux( io.dat.br_ltu, PC_BR, PC_4),
                     Mux(cs_br_type === BR_J  ,  PC_J,
                     Mux(cs_br_type === BR_JR ,  PC_JR,
                                                 PC_4))))))))))
                           
   val stall =  !io.imem.resp.valid || !((cs_mem_en && io.dmem.resp.valid) || !cs_mem_en) || io.resetSignal
 
   // Set the data-path control signals
   io.ctl.stall    := stall
   io.ctl.pc_sel   := ctrl_pc_sel
   io.ctl.op1_sel  := cs_op1_sel
   io.ctl.op2_sel  := cs_op2_sel
   io.ctl.alu_fun  := cs_alu_fun
   io.ctl.wb_sel   := cs_wb_sel
   io.ctl.rf_wen   := Mux(stall || io.ctl.exception, Bool(false), cs_rf_wen)
  
   // convert CSR instructions with raddr1 == 0 to read-only CSR commands
   val rs1_addr = io.dat.inst(RS1_MSB, RS1_LSB)
   val csr_ren = (cs_csr_cmd === CSR.S || cs_csr_cmd === CSR.C) && rs1_addr === UInt(0)
   val csr_cmd = Mux(csr_ren, CSR.R, cs_csr_cmd)

   io.ctl.csr_cmd  := Mux(stall, CSR.N, csr_cmd)
   
   // Memory Requests
   io.imem.req.valid    := Bool(true)
   io.imem.req.bits.fcn := M_XRD
   io.imem.req.bits.typ := MT_WU

   io.dmem.req.valid    := cs_mem_en
   io.dmem.req.bits.fcn := cs_mem_fcn
   io.dmem.req.bits.typ := cs_msk_sel

   // Exception Handling ---------------------
   
   // We only need to check if the instruction is illegal (or unsupported)
   // or if the CSR file wants us to be interrupted.
   // Other exceptions are detected later in the pipeline by passing the
   // instruction to the CSR File and letting it redirect the PC as it sees
   // fit.
   val exc_illegal = (!cs_val_inst && io.imem.resp.valid) 
 
   io.ctl.exception := exc_illegal || 
                        io.dat.csr_interrupt
//                        io.dat.csr.interrupt ||
//   io.ctl.exc_cause := Mux(io.dat.csr.interrupt, io.dat.csr.interrupt_cause, 
   io.ctl.exc_cause := Mux(io.dat.csr_interrupt, io.dat.csr_interrupt_cause, 
                                            UInt(Common.Causes.illegal_instruction))
   
   // ----------------------------------------       
   
   // only here to thread ctrl signals to printf in dpath.scala                  
   io.ctl.debug_dmem_val := cs_mem_en
   io.ctl.debug_dmem_typ := cs_msk_sel
}

}
