//**************************************************************************
// RISCV Processor Control Path
//--------------------------------------------------------------------------
//
// cpath must check io.imem.resp.valid to verify it's decoding an actual
// instruction. Otherwise, it is in charge of muxing off ctrl signals.

package Sodor
{

import Chisel._
import Node._

import Common._
import Common.Instructions._
import Constants._
import ALU._

class CtrlSignals extends Bundle() 
{
   val exe_kill  = Bool()    // squash EX stage (exception/sret occurred)
   val pc_sel    = UInt(width = PC_4.getWidth) 
   val brjmp_sel = Bool()
   val op1_sel   = UInt(width = OP1_X.getWidth) 
   val op2_sel   = UInt(width = OP2_X.getWidth) 
   val alu_fun   = Bits(width = SZ_ALU_FN) 
   val wb_sel    = UInt(width = WB_X.getWidth) 
   val rf_wen    = Bool() 
   val bypassable= Bool()     // instruction's result can be bypassed
   val csr_cmd   = UInt(width = 3) 

   val dmem_val  = Bool()
   val dmem_fcn  = Bits(width = M_X.getWidth)
   val dmem_typ  = Bits(width = MT_X.getWidth)
 
   // confusing point: these three signals come out in WB
   val exception = Bool()   
   val exc_cause = UInt(width = 6)
   val sret      = Bool()
}

class CpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val imem = new FrontEndCpuIO().flip()
   val dmem = new MemPortIo(conf.xprlen)
   val dat  = new DatToCtlIo().flip()
   val ctl  = new CtrlSignals().asOutput
   override def clone = { new CpathIo().asInstanceOf[this.type] }
}

                                                                                                                            
class CtlPath(implicit conf: SodorConfiguration) extends Module
{                            //   
                             //   inst val?                                                                                mem flush/sync
                             //   |    br type                     alu fcn                 bypassable?                     |    is sret
  val io = new CpathIo()     //   |    |     is jmp?               |        wb sel         |  mem en               csr cmd |    |  is syscall
                             //   |    |     |  op1 sel  op2 sel   |        |       rf wen |  |      mem cmd       |       |    |  |  is sbreak
   val csignals =            //   |    |     |  |        |         |        |       |      |  |      |      mask type      |    |  |  |  is privileged
      ListLookup(io.imem.resp.bits.inst,//   |  |        |         |        |       |      |  |      |      |      |       |    |  |  |  | 
                             List(N, BR_N  , N, OP1_X  , OP2_X  , ALU_X   , WB_X,   REN_0, N, MEN_0, M_X  , MT_X,  CSR.N,  M_N, N, N, N, N),
               Array(       //
                  LW      -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_ADD , WB_MEM, REN_1, N, MEN_1, M_XRD, MT_W,  CSR.N, M_N, N, N, N, N),
                  LB      -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_ADD , WB_MEM, REN_1, N, MEN_1, M_XRD, MT_B,  CSR.N, M_N, N, N, N, N),
                  LBU     -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_ADD , WB_MEM, REN_1, N, MEN_1, M_XRD, MT_BU, CSR.N, M_N, N, N, N, N),
                  LH      -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_ADD , WB_MEM, REN_1, N, MEN_1, M_XRD, MT_H,  CSR.N, M_N, N, N, N, N),
                  LHU     -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_ADD , WB_MEM, REN_1, N, MEN_1, M_XRD, MT_HU, CSR.N, M_N, N, N, N, N),
                  SW      -> List(Y, BR_N  , N, OP1_RS1, OP2_IMS , ALU_ADD , WB_X  , REN_0, N, MEN_1, M_XWR, MT_W,  CSR.N, M_N, N, N, N, N),
                  SB      -> List(Y, BR_N  , N, OP1_RS1, OP2_IMS , ALU_ADD , WB_X  , REN_0, N, MEN_1, M_XWR, MT_B,  CSR.N, M_N, N, N, N, N),
                  SH      -> List(Y, BR_N  , N, OP1_RS1, OP2_IMS , ALU_ADD , WB_X  , REN_0, N, MEN_1, M_XWR, MT_H,  CSR.N, M_N, N, N, N, N),
                  
                  AUIPC   -> List(Y, BR_N  , N, OP1_IMU, OP2_PC  , ALU_ADD  ,WB_ALU, REN_1, Y, MEN_0, M_X ,  MT_X,  CSR.N, M_N, N, N, N, N),
                  LUI     -> List(Y, BR_N  , N, OP1_IMU, OP2_X   , ALU_COPY1,WB_ALU, REN_1, Y, MEN_0, M_X ,  MT_X,  CSR.N, M_N, N, N, N, N),
                 
                  ADDI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_ADD , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  ANDI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_AND , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  ORI     -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_OR  , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  XORI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_XOR , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  SLTI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_SLT , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  SLTIU   -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_SLTU, WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  SLLI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_SLL , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  SRAI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_SRA , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  SRLI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI , ALU_SRL , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                   
                  SLL     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2 , ALU_SLL , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  ADD     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2 , ALU_ADD , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  SUB     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2 , ALU_SUB , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  SLT     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2 , ALU_SLT , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  SLTU    -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2 , ALU_SLTU, WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  AND     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2 , ALU_AND , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  OR      -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2 , ALU_OR  , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  XOR     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2 , ALU_XOR , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  SRA     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2 , ALU_SRA , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  SRL     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2 , ALU_SRL , WB_ALU, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  
                  JAL     -> List(Y, BR_J  , Y, OP1_X  , OP2_X   , ALU_X   , WB_PC4, REN_1, Y, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  JALR    -> List(Y, BR_JR , Y, OP1_RS1, OP2_IMI , ALU_X   , WB_PC4, REN_1, N, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  BEQ     -> List(Y, BR_EQ , N, OP1_X  , OP2_X   , ALU_X   , WB_X  , REN_0, N, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  BNE     -> List(Y, BR_NE , N, OP1_X  , OP2_X   , ALU_X   , WB_X  , REN_0, N, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  BGE     -> List(Y, BR_GE , N, OP1_X  , OP2_X   , ALU_X   , WB_X  , REN_0, N, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  BGEU    -> List(Y, BR_GEU, N, OP1_X  , OP2_X   , ALU_X   , WB_X  , REN_0, N, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  BLT     -> List(Y, BR_LT , N, OP1_X  , OP2_X   , ALU_X   , WB_X  , REN_0, N, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
                  BLTU    -> List(Y, BR_LTU, N, OP1_X  , OP2_X   , ALU_X   , WB_X  , REN_0, N, MEN_0, M_X  , MT_X,  CSR.N, M_N, N, N, N, N),
  
                  CSRRWI  -> List(Y, BR_N  , N, OP1_IMZ, OP2_X   , ALU_COPY1,WB_CSR, REN_1, N, MEN_0, M_X ,  MT_X,  CSR.W, M_N, N, N, N, N),
                  CSRRSI  -> List(Y, BR_N  , N, OP1_IMZ, OP2_X   , ALU_COPY1,WB_CSR, REN_1, N, MEN_0, M_X ,  MT_X,  CSR.S, M_N, N, N, N, N),
                  CSRRW   -> List(Y, BR_N  , N, OP1_RS1, OP2_X   , ALU_COPY1,WB_CSR, REN_1, N, MEN_0, M_X ,  MT_X,  CSR.W, M_N, N, N, N, N),
                  CSRRS   -> List(Y, BR_N  , N, OP1_RS1, OP2_X   , ALU_COPY1,WB_CSR, REN_1, N, MEN_0, M_X ,  MT_X,  CSR.S, M_N, N, N, N, N),
                  CSRRC   -> List(Y, BR_N  , N, OP1_RS1, OP2_X   , ALU_COPY1,WB_CSR, REN_1, N, MEN_0, M_X ,  MT_X,  CSR.C, M_N, N, N, N, N),
                  CSRRCI  -> List(Y, BR_N  , N, OP1_IMZ, OP2_X   , ALU_COPY1,WB_CSR, REN_1, N, MEN_0, M_X ,  MT_X,  CSR.C, M_N, N, N, N, N),
        
                  SCALL   -> List(Y, BR_N  , N, OP1_X  , OP2_X   , ALU_X   , WB_X  , REN_0, N, MEN_0, M_X  , MT_X,  CSR.N, M_FD,N, Y, N, N), 
                  SRET    -> List(Y, BR_N  , N, OP1_X  , OP2_X   , ALU_X   , WB_X  , REN_0, N, MEN_0, M_X  , MT_X,  CSR.N, M_FD,Y, N, N, Y), 
                  SBREAK  -> List(Y, BR_N  , N, OP1_X  , OP2_X   , ALU_X   , WB_X  , REN_0, N, MEN_0, M_X  , MT_X,  CSR.N, M_FD,N, N, Y, N), 

                  FENCE_I -> List(Y, BR_N  , N, OP1_X  , OP2_X   , ALU_X   , WB_X  , REN_0, N, MEN_0, M_X  , MT_X,  CSR.N, M_SI,N, N, N, N), 
                  FENCE   -> List(Y, BR_N  , N, OP1_X  , OP2_X   , ALU_X   , WB_X  , REN_0, N, MEN_1, M_X  , MT_X,  CSR.N, M_SD,N, N, N, N)
                  // we are already sequentially consistent, so no need to honor the fence instruction
                  ))



   // Put these control signals in variables
   val (cs_inst_val: Bool) :: cs_br_type :: cs_brjmp_sel      :: cs_op1_sel            :: cs_op2_sel ::                     cs0 = csignals
   val cs_alu_fun          :: cs_wb_sel  :: (cs_rf_wen: Bool) :: (cs_bypassable: Bool) ::                                   cs1 = cs0
   val (cs_mem_en: Bool)   :: cs_mem_fcn :: cs_msk_sel        ::                                                            cs2 = cs1
   val cs_csr_cmd :: cs_sync_fcn :: (cs_sret: Bool) :: (cs_syscall: Bool) :: (cs_sbreak: Bool) :: (cs_privileged: Bool) ::  Nil = cs2


   // Is the instruction valid? If not, mux off all control signals!
   val ctrl_valid = io.imem.resp.valid
                           
   // Branch Logic   
   val take_evec = Bool() // jump to the csr.io.evec target 
                          // (for exceptions or sret, taken in the WB stage)
   val exe_exception = Bool() 

   val ctrl_pc_sel = Mux(take_evec            ,  PC_EXC,
                     Mux(cs_br_type === BR_N  ,  PC_4,
                     Mux(cs_br_type === BR_NE ,  Mux(!io.dat.br_eq,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_EQ ,  Mux( io.dat.br_eq,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_GE ,  Mux(!io.dat.br_lt,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_GEU,  Mux(!io.dat.br_ltu, PC_BR, PC_4),
                     Mux(cs_br_type === BR_LT ,  Mux( io.dat.br_lt,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_LTU,  Mux( io.dat.br_ltu, PC_BR, PC_4),
                     Mux(cs_br_type === BR_J  ,  PC_J,
                     Mux(cs_br_type === BR_JR ,  PC_JR,
                     PC_4
                     ))))))))))
                           

   io.imem.req.valid := !(ctrl_pc_sel === PC_4) && ctrl_valid

   io.ctl.exe_kill   := take_evec
   io.ctl.pc_sel     := ctrl_pc_sel
   io.ctl.brjmp_sel  := cs_brjmp_sel.toBool
   io.ctl.op1_sel    := cs_op1_sel
   io.ctl.op2_sel    := cs_op2_sel
   io.ctl.alu_fun    := cs_alu_fun
   io.ctl.wb_sel     := cs_wb_sel
   io.ctl.rf_wen     := Mux(exe_exception || !ctrl_valid, Bool(false), cs_rf_wen.toBool)
   io.ctl.bypassable := cs_bypassable.toBool 
   io.ctl.csr_cmd    := Mux(exe_exception || !ctrl_valid, CSR.N, cs_csr_cmd)
   
   // Memory Requests
   io.ctl.dmem_val   := cs_mem_en.toBool && ctrl_valid
   io.ctl.dmem_fcn   := cs_mem_fcn
   io.ctl.dmem_typ   := cs_msk_sel


   //-------------------------------
   // Exception Handling

   val exc_illegal = (!cs_inst_val && io.imem.resp.valid) 

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
   val rs1_addr       = io.imem.resp.bits.inst(RS1_MSB,RS1_LSB)
   val csr_addr       = io.imem.resp.bits.inst(CSR_ADDR_MSB, CSR_ADDR_LSB)
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
   

   exe_exception := cs_syscall       ||
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

   take_evec        := (exe_exception || cs_sret) && cs_inst_val
   io.ctl.sret      := cs_sret && cs_inst_val
   io.ctl.exception := exe_exception && cs_inst_val
   
}

}
