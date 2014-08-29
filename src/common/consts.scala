package Common
package constants
{

import Chisel._
import scala.math._

//trait InterruptConstants 
//{
//   val CAUSE_INTERRUPT = 32
//   val IRQ_IPI = 5
//   val IRQ_TIMER = 7
//}

trait RISCVConstants
{
   val START_ADDR = 0x2000

   // abstract out instruction decode magic numbers
   val RD_MSB  = 11
   val RD_LSB  = 7
   val RS1_MSB = 19
   val RS1_LSB = 15
   val RS2_MSB = 24
   val RS2_LSB = 20

   val CSR_ADDR_MSB = 31
   val CSR_ADDR_LSB = 20

   // location of the fifth bit in the shamt (for checking for illegal ops for SRAIW,etc.)
   val SHAMT_5_BIT = 25
   val LONGEST_IMM_SZ = 20
   val X0 = UInt(0)
   val RA = UInt(1) // return address register
 
   // The Bubble Instruction (Machine generated NOP)
   // Insert (XOR x0,x0,x0) which is different from software compiler 
   // generated NOPs which are (ADDI x0, x0, 0).
   // Reasoning for this is to let visualizers and stat-trackers differentiate
   // between software NOPs and machine-generated Bubbles in the pipeline.
   val BUBBLE  = Bits(0x4033, 32)
}


trait ExcCauseConstants
{
  // Exception Causes
  // itlb == 1
  val EXC_CAUSE_SZ = 5
  val EXCEPTION_ILLEGAL    = UInt(2, EXC_CAUSE_SZ)
  val EXCEPTION_PRIVILEGED = UInt(3, EXC_CAUSE_SZ)
  //fpu == 4
  val EXCEPTION_SCALL    = UInt(6, EXC_CAUSE_SZ)
  //ma ld == 8
  //ma st == 9
  //dtlb ld == 10
  //dtlb st == 11
  //xcpt_vec disabled == 12
  //inst addr misaligned == 0
  val EXC_RETURN = UInt(31, EXC_CAUSE_SZ)
}
}

 
