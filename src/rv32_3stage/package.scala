package RV32_3stage
import RV32_3stage.constants._

import chisel3._
import chisel3.util._
import scala.math._
import config._

//TODO: When compiler bug SI-5604 is fixed in 2.10, change object Constants to 
//      package object rocket and remove import Constants._'s from other files
object Constants extends
   ScalarOpConstants with
   Common.constants.RISCVConstants with
   Common.MemoryOpConstants with
   Common.constants.PrivilegedConstants
{
	case object NUM_MEMORY_PORTS extends Field[Int]
	// if the front-end ONLY predicts PC+4, this simplifies quite a bit of logic.
	// First, the PC select mux never needs to compute ExePC + 4 on a branch
	// redirect (since PC+4 is always predicted).
	// Second, JAL can write-back to rd the ExePC, since it will already be PC+4
	// relative to the JAL.
	// no BTB, etc, added yet
	case object PREDICT_PCP4 extends Field[Boolean]
	case object PRINT_COMMIT_LOG extends Field[Boolean]
}
