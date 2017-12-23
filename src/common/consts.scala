package Common
package constants
{

import chisel3._
import chisel3.util._
import scala.math._

trait RISCVConstants
{
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

   // The Bubble Instruction (Machine generated NOP)
   // Insert (XOR x0,x0,x0) which is different from software compiler
   // generated NOPs which are (ADDI x0, x0, 0).
   // Reasoning for this is to let visualizers and stat-trackers differentiate
   // between software NOPs and machine-generated Bubbles in the pipeline.
   val BUBBLE  = Bits(0x4033, 32)
}

trait PrivilegedConstants
{
   val MTVEC = 0x100
   val START_ADDR = "h80000000".U //MTVEC + 0x100

   val SZ_PRV = 2
   val PRV_U = 0
   val PRV_S = 1
   val PRV_H = 2
   val PRV_M = 3
}

trait AddressConstants
{
   // 32 bit address space (4 kB pages)
   val PADDR_BITS = 32
}

}


object Constants extends
   constants.AddressConstants with
   MemoryOpConstants with
   constants.PrivilegedConstants
{
}

