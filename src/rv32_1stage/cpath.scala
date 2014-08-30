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
   val csr_cmd   = UInt(OUTPUT, 2) 
   val exception = Bool(OUTPUT)
   val exc_cause = UInt(OUTPUT, 6)
   val sret      = Bool(OUTPUT)

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
{                                                                                                                   // is sret
  val io = new CpathIo()                                                                                            // |  is syscall
                                                                                                                    // |  |  is sbreak
   val csignals =                                                                                                   // |  |  |  is privileged
      ListLookup(io.dat.inst,                                                                                       // |  |  |  |
                            List(N, BR_N  , OP1_X  ,  OP2_X   , ALU_X   , WB_X   , REN_0, MEN_0, M_X   , MT_X,  CSR.N, N, N, N, N),
               Array(       /* val  |  BR  |  op1   |   op2     |  ALU    |  wb  | rf   | mem  | mem  | mask |  csr  */
                            /* inst | type |   sel  |    sel    |   fcn   |  sel | wen  |  en  |  wr  | type |  cmd  */
                  LW      -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_W,  CSR.N, N, N, N, N),
                  LB      -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_B,  CSR.N, N, N, N, N),
                  LBU     -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_BU, CSR.N, N, N, N, N),
                  LH      -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_H,  CSR.N, N, N, N, N),
                  LHU     -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_MEM, REN_1, MEN_1, M_XRD, MT_HU, CSR.N, N, N, N, N),
                  SW      -> List(Y, BR_N  , OP1_RS1, OP2_IMS , ALU_ADD ,  WB_X  , REN_0, MEN_1, M_XWR, MT_W,  CSR.N, N, N, N, N),
                  SB      -> List(Y, BR_N  , OP1_RS1, OP2_IMS , ALU_ADD ,  WB_X  , REN_0, MEN_1, M_XWR, MT_B,  CSR.N, N, N, N, N),
                  SH      -> List(Y, BR_N  , OP1_RS1, OP2_IMS , ALU_ADD ,  WB_X  , REN_0, MEN_1, M_XWR, MT_H,  CSR.N, N, N, N, N),
                  
                  AUIPC   -> List(Y, BR_N  , OP1_IMU, OP2_PC  , ALU_ADD ,  WB_ALU, REN_1, MEN_0, M_X ,  MT_X,  CSR.N, N, N, N, N),
                  LUI     -> List(Y, BR_N  , OP1_IMU, OP2_X   , ALU_COPY1, WB_ALU, REN_1, MEN_0, M_X ,  MT_X,  CSR.N, N, N, N, N),
                 
                  ADDI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_ADD ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  ANDI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_AND ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  ORI     -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_OR  ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  XORI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_XOR ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  SLTI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SLT ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  SLTIU   -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SLTU,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  SLLI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SLL ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  SRAI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SRA ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  SRLI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI , ALU_SRL ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                   
                  SLL     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SLL ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  ADD     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_ADD ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  SUB     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SUB ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  SLT     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SLT ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  SLTU    -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SLTU,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  AND     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_AND ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  OR      -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_OR  ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  XOR     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_XOR ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  SRA     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SRA ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  SRL     -> List(Y, BR_N  , OP1_RS1, OP2_RS2 , ALU_SRL ,  WB_ALU, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  
                  JAL     -> List(Y, BR_J  , OP1_X  , OP2_X   , ALU_X   ,  WB_PC4, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  JALR    -> List(Y, BR_JR , OP1_RS1, OP2_IMI , ALU_X   ,  WB_PC4, REN_1, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  BEQ     -> List(Y, BR_EQ , OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  BNE     -> List(Y, BR_NE , OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  BGE     -> List(Y, BR_GE , OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  BGEU    -> List(Y, BR_GEU, OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  BLT     -> List(Y, BR_LT , OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
                  BLTU    -> List(Y, BR_LTU, OP1_X  , OP2_X   , ALU_X   ,  WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N),
  
                  CSRRWI  -> List(Y, BR_N  , OP1_IMZ, OP2_X   , ALU_COPY1,WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.W, N, N, N, N),
                  CSRRSI  -> List(Y, BR_N  , OP1_IMZ, OP2_X   , ALU_COPY1,WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.S, N, N, N, N),
                  CSRRW   -> List(Y, BR_N  , OP1_RS1, OP2_X   , ALU_COPY1,WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.W, N, N, N, N),
                  CSRRS   -> List(Y, BR_N  , OP1_RS1, OP2_X   , ALU_COPY1,WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.S, N, N, N, N),
                  CSRRC   -> List(Y, BR_N  , OP1_RS1, OP2_X   , ALU_COPY1,WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.C, N, N, N, N),
                  CSRRCI  -> List(Y, BR_N  , OP1_IMZ, OP2_X   , ALU_COPY1,WB_CSR, REN_1, MEN_0, M_X ,  MT_X,  CSR.C, N, N, N, N),
           
        
                  SCALL   -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N, N, Y, N, N), 
                  SRET    -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N, Y, N, N, Y), 
                  SBREAK  -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N, N, N, Y, N), 

                  FENCE_I -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_0, M_X  , MT_X,  CSR.N, N, N, N, N), 
                  FENCE   -> List(Y, BR_N  , OP1_X  , OP2_X  ,  ALU_X    , WB_X  , REN_0, MEN_1, M_X  , MT_X,  CSR.N, N, N, N, N)
                  // we are already sequentially consistent, so no need to honor the fence instruction
                  ))

   // Put these control signals into variables
   val (cs_val_inst: Bool) :: cs_br_type         :: cs_op1_sel            :: cs_op2_sel            :: cs0 = csignals
   val cs_alu_fun          :: cs_wb_sel          :: (cs_rf_wen: Bool)     ::                          cs1 = cs0
   val (cs_mem_en: Bool)   :: cs_mem_fcn         :: cs_msk_sel            :: cs_csr_cmd            :: cs2 = cs1
   val (cs_sret: Bool)     :: (cs_syscall: Bool) :: (cs_sbreak: Bool)     :: (cs_privileged: Bool) :: Nil = cs2

                           
   // Branch Logic   
   val ctrl_pc_sel = Mux(io.ctl.exception || cs_sret,  PC_EXC,
                     Mux(cs_br_type === BR_N        ,  PC_4,
                     Mux(cs_br_type === BR_NE       ,  Mux(!io.dat.br_eq,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_EQ       ,  Mux( io.dat.br_eq,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_GE       ,  Mux(!io.dat.br_lt,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_GEU      ,  Mux(!io.dat.br_ltu, PC_BR, PC_4),
                     Mux(cs_br_type === BR_LT       ,  Mux( io.dat.br_lt,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_LTU      ,  Mux( io.dat.br_ltu, PC_BR, PC_4),
                     Mux(cs_br_type === BR_J        ,  PC_J,
                     Mux(cs_br_type === BR_JR       ,  PC_JR,
                                                       PC_4))))))))))
                           
   val stall =  !io.imem.resp.valid || !((cs_mem_en && io.dmem.resp.valid) || !cs_mem_en) || io.resetSignal

   io.ctl.stall    := stall
   io.ctl.pc_sel   := ctrl_pc_sel
   io.ctl.op1_sel  := cs_op1_sel
   io.ctl.op2_sel  := cs_op2_sel
   io.ctl.alu_fun  := cs_alu_fun
   io.ctl.wb_sel   := cs_wb_sel
   io.ctl.rf_wen   := Mux(stall, Bool(false), cs_rf_wen)
   io.ctl.csr_cmd  := Mux(stall, CSR.N, cs_csr_cmd)
   
   // Memory Requests
   io.imem.req.valid    := Bool(true)
   io.imem.req.bits.fcn := M_XRD
   io.imem.req.bits.typ := MT_WU

   io.dmem.req.valid    := cs_mem_en
   io.dmem.req.bits.fcn := cs_mem_fcn
   io.dmem.req.bits.typ := cs_msk_sel

   // Exception Handling ---------------------
   
   val exc_illegal = (!cs_val_inst && io.imem.resp.valid) 

   // check for interrupts 
   // an interrupt must be both pending (ip) and enabled on the interrupt mask (im)
   var exc_interrupts = (0 until io.dat.status.ip.getWidth).map(i => (io.dat.status.im(i) && io.dat.status.ip(i), UInt(BigInt(1) << (conf.xprlen-1) | i)))
   val (exc_interrupt_unmasked, exc_interrupt_cause) = checkExceptions(exc_interrupts)
   val exc_interrupt = io.dat.status.ei && exc_interrupt_unmasked

   def checkExceptions(x: Seq[(Bool, UInt)]) =
      (x.map(_._1).reduce(_||_), PriorityMux(x))
     
   // check for illegal CSR instructions or CSR access violations
   val fp_csrs        = Common.CSRs.fcsr :: Common.CSRs.frm :: Common.CSRs.fflags :: Nil
   val legal_csrs     = Common.CSRs.all32.toSet -- fp_csrs
   val rs1_addr       = io.dat.inst(RS1_MSB,RS1_LSB)
   val csr_addr       = io.dat.inst(CSR_ADDR_MSB, CSR_ADDR_LSB)
   val csr_en         = cs_csr_cmd != CSR.N
   val csr_wen        = rs1_addr != UInt(0) || !Vec(CSR.S, CSR.C).contains(cs_csr_cmd)
   val exc_csr_privileged = csr_en &&
                        (csr_addr(11,10) === UInt(3) && csr_wen ||
                         csr_addr(11,10) === UInt(2) ||
                         csr_addr(11,10) === UInt(1) && !io.dat.status.s ||
                         csr_addr(9,8) >= UInt(2) ||
                         csr_addr(9,8) === UInt(1) && !io.dat.status.s && csr_wen)
   val csr_invalid    = csr_en && !Vec(legal_csrs.map(UInt(_))).contains(csr_addr)

   val exc_privileged = exc_csr_privileged || (cs_privileged && !(io.dat.status.s))
   
   io.ctl.sret      := cs_sret

   io.ctl.exception := cs_syscall       ||
                       cs_sbreak        ||
                       exc_illegal      ||
                       csr_invalid      ||
                       exc_privileged   ||
                       exc_interrupt

   // note: priority here is very important
   io.ctl.exc_cause := Mux(exc_interrupt,              exc_interrupt_cause,
                       Mux(exc_illegal || csr_invalid, UInt(Common.Causes.illegal_instruction),
                       Mux(exc_privileged,             UInt(Common.Causes.privileged_instruction),
                       Mux(cs_syscall,                 UInt(Common.Causes.syscall),
                       Mux(cs_sbreak,                  UInt(Common.Causes.breakpoint),
                                                       UInt(0,5))))))
   
   
   // ----------------------------------------       
   
   // only here to thread ctrl signals to printf in dpath.scala                  
   io.ctl.debug_dmem_val := cs_mem_en
   io.ctl.debug_dmem_typ := cs_msk_sel
}

}
