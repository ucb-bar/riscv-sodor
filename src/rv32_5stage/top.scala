package Sodor

import Chisel._
import Node._
import Constants._
import Common._
import Common.Util._
import ReferenceChipBackend._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap


object ReferenceChipBackend {
  val initMap = new HashMap[Module, Bool]()
}

class TopIO() extends Bundle  {
  val debug = new Common.DebugIO
  val htif  = new Common.HTIFIO()
}

class Top extends Module 
{
   val io = new TopIO()

   implicit val sodor_conf = SodorConfiguration()

   val reset_signal = RegUpdate(RegUpdate(io.htif.reset))
   val tile = Module(new SodorTile)
  
   tile.io.host.reset := reset_signal
   tile.io.host.pcr_req <> Queue(io.htif.pcr_req)
   io.htif.pcr_rep <> Queue(tile.io.host.pcr_rep)

   tile.io.host.mem_req <> Queue(io.htif.mem_req)
   io.htif.mem_rep <> tile.io.host.mem_rep

   io.debug.error_mode := RegUpdate(tile.io.host.debug.error_mode)
}

object elaborate {
  def main(args: Array[String]): Unit = {
    chiselMain(args, () => Module(new Top()))
  }
}
