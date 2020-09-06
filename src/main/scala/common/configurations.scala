package sodor.common

import chisel3._
import chisel3.util._

import Constants._
import freechips.rocketchip.config.Parameters

case class SodorConfiguration(
   // Configuration for Chipyard integration
   val p: Parameters,
   val chipyardBuild: Boolean = false,
   val xprlen: Int = 32,
   val ports: Int = 2
)
