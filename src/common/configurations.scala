package Common
{
import Chisel._
import Node._

import Constants._
   
case class SodorConfiguration
{
   val xprlen = 32
   val nxpr = 32
   val nxprbits = log2Up(nxpr)
   val rvc = false
   val vm = false
}


}
