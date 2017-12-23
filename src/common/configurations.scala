package Common
{
import chisel3._
import chisel3.util._
import config._
import Constants._

// for debugging, print out the commit information.
// can be compared against the riscv-isa-run Spike ISA simulator's commit logger.
case object PRINT_COMMIT_LOG extends Field[Boolean]
case object xprlen extends Field[Int]
case object usingUser extends Field[Boolean]

class SodorConfiguration extends Config((site, here, up) => {
	case Common.xprlen => 32
	case Common.usingUser => false
    case PRINT_COMMIT_LOG => false
})
   
}
