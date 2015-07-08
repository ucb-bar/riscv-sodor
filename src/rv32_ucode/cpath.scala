//**************************************************************************
// RISCV U-Coded Processor Control Path
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 May 28

package Sodor
{

import Chisel._
import Node._

import Common._
import Common.Instructions._
import Constants._
import scala.collection.mutable.ArrayBuffer
 

class CtlToDatIo extends Bundle() 
{
   val msk_sel = UInt(OUTPUT, MSK_SZ)
   val csr_cmd = UInt(OUTPUT, CSR.SZ)
   val ld_ir   = Bool(OUTPUT)
   val reg_sel = UInt(OUTPUT, RS_X.getWidth())
   val reg_wr  = Bool(OUTPUT)
   val en_reg  = Bool(OUTPUT)
   val ld_a    = Bool(OUTPUT)
   val ld_b    = Bool(OUTPUT)
   val alu_op  = UInt(OUTPUT, ALU_X.getWidth())
   val en_alu  = Bool(OUTPUT)
   val ld_ma   = Bool(OUTPUT)
   val mem_wr  = Bool(OUTPUT)
   val en_mem  = Bool(OUTPUT)
   val is_sel  = UInt(OUTPUT, IS_X.getWidth())
   val en_imm  = Bool(OUTPUT)
   val upc     = UInt(OUTPUT) // for debugging purposes 
   val upc_is_fetch = Bool(OUTPUT) // for debugging purposes 
}

class CpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val mem  = new MemPortIo(conf.xprlen)
   val dat  = new DatToCtlIo().flip()
   val ctl  = new CtlToDatIo()
   override def clone = { new CpathIo().asInstanceOf[this.type] }
}

 

class CtlPath(implicit conf: SodorConfiguration) extends Module
{
   val io = new CpathIo()

   // Compile the Micro-code down into a ROM 
   val (label_target_map, label_sz) = MicrocodeCompiler.constructLabelTargetMap(Microcode.codes)
   val rombits                      = MicrocodeCompiler.emitRomBits(Microcode.codes, label_target_map, label_sz)
   val opcode_dispatch_table        = MicrocodeCompiler.generateDispatchTable(label_target_map)
        
   
   // Macro Instruction Opcode Dispatch Table
   val upc_opgroup_target = MuxLookup (io.dat.inst, UInt(label_target_map("ILLEGAL"), label_sz),
                                                    opcode_dispatch_table)

   // Micro-PC State Register
   val upc_state_next = UInt()
   val upc_state = Reg(UInt(), next = upc_state_next, init = UInt(label_target_map("INIT_PC"), label_sz))

   // Micro-code ROM
   val micro_code = Vec(rombits)
   val uop = micro_code(upc_state)
    
   // Extract Control Signals from UOP
   val cs = new Bundle()
            {
               val msk_sel        = UInt(width = MSK_SZ)
               val csr_cmd        = UInt(width = CSR.SZ)
               val ld_ir          = Bool()  
               val reg_sel        = UInt(width = RS_X.getWidth())  
               val reg_wr         = Bool()  
               val en_reg         = Bool()  
               val ld_a           = Bool()  
               val ld_b           = Bool()  
               val alu_op         = UInt(width = ALU_X.getWidth())  
               val en_alu         = Bool()  
               val ld_ma          = Bool()  
               val mem_wr         = Bool()  
               val en_mem         = Bool()  
               val is_sel         = UInt(width = IS_X.getWidth())  
               val en_imm         = Bool()  
               val ubr            = UInt(width = UBR_N.getWidth())  
               val upc_rom_target = UInt(width = label_sz)  
               override def clone = this.asInstanceOf[this.type]
            }.fromNode(uop)
   require(label_sz == 8, "Label size must be 8")

   val mem_is_busy = !(io.mem.resp.valid)

   // Micro-PC State Logic
   val upc_sel     = MuxCase(UPC_CURRENT, Array(
                      (cs.ubr === UBR_N) -> UPC_NEXT,
                      (cs.ubr === UBR_D) -> UPC_DISPATCH,
                      (cs.ubr === UBR_J) -> UPC_ABSOLUTE,
                      (cs.ubr === UBR_EZ)-> Mux ( io.dat.alu_zero, UPC_ABSOLUTE , UPC_NEXT),
                      (cs.ubr === UBR_NZ)-> Mux (~io.dat.alu_zero, UPC_ABSOLUTE , UPC_NEXT),
                      (cs.ubr === UBR_S) -> Mux (mem_is_busy     , UPC_CURRENT  , UPC_NEXT)
                    ))
 
    
   upc_state_next := MuxCase(upc_state, Array(
                      (upc_sel === UPC_DISPATCH) -> upc_opgroup_target,
                      (upc_sel === UPC_ABSOLUTE) -> cs.upc_rom_target,
                      (upc_sel === UPC_NEXT)     -> (upc_state + UInt(1)),
		                (upc_sel === UPC_CURRENT)  -> upc_state
                    ))

   
   // Exception Handling ---------------------
   // TODO handle exceptions
   // somehow have illegal micro-code send an exception to the CSRFile

//   val exc_illegal = (!cs_val_inst && io.imem.resp.valid) 
//    
//   io.ctl.exception := exc_illegal || io.dat.csr_interrupt
//   io.ctl.exc_cause := Mux(io.dat.csr_interrupt, io.dat.csr_interrupt_cause, 
//                                            UInt(Common.Causes.illegal_instruction))
       

   // Cpath Control Interface
   io.ctl.msk_sel := cs.msk_sel
   io.ctl.ld_ir   := cs.ld_ir      
   io.ctl.reg_sel := cs.reg_sel   
   io.ctl.reg_wr  := cs.reg_wr     
   io.ctl.en_reg  := cs.en_reg     
   io.ctl.ld_a    := cs.ld_a       
   io.ctl.ld_b    := cs.ld_b      
   io.ctl.alu_op  := cs.alu_op     
   io.ctl.en_alu  := cs.en_alu     
   io.ctl.ld_ma   := cs.ld_ma      
   io.ctl.mem_wr  := cs.mem_wr     
   io.ctl.en_mem  := cs.en_mem     
   io.ctl.is_sel  := cs.is_sel     
   io.ctl.en_imm  := cs.en_imm     

   // convert CSR instructions with raddr1 == 0 to read-only CSR commands
   val rs1_addr = io.dat.inst(RS1_MSB, RS1_LSB)
   val csr_ren = (cs.csr_cmd === CSR.S || cs.csr_cmd === CSR.C) && rs1_addr === UInt(0)
   val csr_cmd = Mux(csr_ren, CSR.R, cs.csr_cmd)
   io.ctl.csr_cmd := csr_cmd




   io.ctl.upc := upc_state
   io.ctl.upc_is_fetch := (upc_state === UInt(label_target_map("FETCH")))
 
   // Memory Interface
   io.mem.req.bits.fcn:= Mux(cs.en_mem && cs.mem_wr, M_XWR, M_XRD)
   io.mem.req.bits.typ:= cs.msk_sel
   io.mem.req.valid   := cs.en_mem.toBool 

}

}
