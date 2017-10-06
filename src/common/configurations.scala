package Common
{
import chisel3._
import chisel3.util._
import config._
import Constants._

case object xprlen extends Field[Int]
case object nxpr extends Field[Int]
case object nxprbits extends Field[Int]
case object usingUser extends Field[Boolean]

class SodorConfiguration extends Config((site, here, up) => {
	case Common.xprlen => 32
	case Common.nxpr => 32
	case Common.nxprbits => 6
	case Common.usingUser => false
})
   
}
