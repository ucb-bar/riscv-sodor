package Common
{
import chisel3._
import chisel3.util._

import Constants._

case class SodorConfiguration()
{
   val xprlen = 32
   val nxpr = 32
   val nxprbits = log2Ceil(nxpr)
   val rvc = false
   val vm = false
   val usingUser = false
}


}
