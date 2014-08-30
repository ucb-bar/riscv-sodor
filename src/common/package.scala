package Common

import Chisel._
import scala.math._

trait AddressConstants {
   // 32 bit address space (4 kB pages)
   val PADDR_BITS = 32
   val VADDR_BITS = 32
   val PGIDX_BITS = 12
   val PPN_BITS = PADDR_BITS-PGIDX_BITS
   val VPN_BITS = VADDR_BITS-PGIDX_BITS
   val ASID_BITS = 7
   val PERM_BITS = 6
}


//TODO: When compiler bug SI-5604 is fixed in 2.10, change object Constants to
//      package object rocket and remove import Constants._'s from other files
object Constants extends
   AddressConstants with
   MemoryOpConstants
{
}
