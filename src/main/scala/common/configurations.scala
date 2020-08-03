package sodor.common

import chisel3._
import chisel3.util._

import Constants._

case class SodorConfiguration(
   // Configuration for Chipyard integration
   val chipyardBuild: Boolean = false,
   val debuglen: Int = 32,
   val xprlen: Int = 32
)
{
   val nxpr = 32
   val nxprbits = log2Ceil(nxpr)
   val rvc = false
   val vm = false
   val usingUser = false
}
