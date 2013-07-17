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
 
trait ExcCauseConstants
{
  // Exception Causes
  // itlb == 1
  val EXC_CAUSE_SZ = 5
  val EXCEPTION_ILLEGAL    = UFix(2, EXC_CAUSE_SZ)
  val EXCEPTION_PRIVILEGED = UFix(3, EXC_CAUSE_SZ)
  //fpu == 4
  val EXCEPTION_SYSCALL    = UFix(6, EXC_CAUSE_SZ)
  //ma ld == 8
  //ma st == 9
  //dtlb ld == 10
  //dtlb st == 11
  //xcpt_vec disabled == 12
  //inst addr misaligned == 0
  val EXC_RETURN = UFix(31, EXC_CAUSE_SZ)
}
}

 
