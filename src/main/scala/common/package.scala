package sodor.common

import chisel3._
import chisel3.util._
import scala.math._


//TODO: When compiler bug SI-5604 is fixed in 2.10, change object Constants to
//      package object rocket and remove import Constants._'s from other files
object Constants extends MemoryOpConstants
{
}
