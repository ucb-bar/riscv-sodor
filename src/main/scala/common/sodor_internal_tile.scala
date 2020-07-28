package sodor.common

import chisel3._
import chisel3.util._

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{LogicalTreeNode}
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem.{RocketCrossingParams}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.amba.axi4._

// Abstract core and tile base class for all cores
abstract class AbstractCore extends Module {
  val mem_ports: Seq[MemPortIo]
}
abstract class AbstractInternalTile(implicit val conf: SodorConfiguration) extends Module {
  val io = IO(new Bundle {
    val debug_port = Flipped(new MemPortIo(data_width = conf.debuglen))
  })
}

// Cores and internal tiles constructors
trait SodorCoreConstructor {
  def instantiate(implicit conf: SodorConfiguration): AbstractCore
}

trait SodorInternalTileConstructor {
  def instantiate(implicit conf: SodorConfiguration): AbstractInternalTile
}

// Original sodor tile in this repo.
// This tile is only for 3-stage core since it has a special structure (SyncMem and one memory port)
class SodorInternalTileStage3(implicit conf: SodorConfiguration) extends AbstractInternalTile
{
  val procConst = {
    class C extends sodor.stage3.constants.SodorProcConstants
    new C
  }

  val core   = Module(new sodor.stage3.Core())
  core.io := DontCare
  val memory = Module(new SyncScratchPadMemory(num_core_ports = procConst.NUM_MEMORY_PORTS))

  if (procConst.NUM_MEMORY_PORTS == 1) // Only used in stage3
  {
    val arbiter = Module(new sodor.stage3.SodorMemArbiter) // only used for single port memory
    core.io.imem <> arbiter.io.imem
    core.io.dmem <> arbiter.io.dmem
    arbiter.io.mem <> memory.io.core_ports(0)
  }
  else
  {
    core.io.imem <> memory.io.core_ports(1)
    core.io.dmem <> memory.io.core_ports(0)
  }

  memory.io.debug_port <> io.debug_port
}

// The general Sodor tile for all cores other than 3-stage
class SodorInternalTile(coreCtor: SodorCoreConstructor)(implicit conf: SodorConfiguration) extends AbstractInternalTile
{
  // notice that while the core is put into reset, the scratchpad needs to be
  // alive so that the HTIF can load in the program.
  val debug = Module(new DebugModule())
  val core   = Module(coreCtor.instantiate)
  core.io := DontCare
  val memory = Module(new AsyncScratchPadMemory(num_core_ports = 2))
  (memory.io.core_ports zip core.mem_ports).foreach(t => t._1 <> t._2)
  io.debug_port <> memory.io.debug_port
}

// Tile constructor
case object Stage1Constructor extends SodorInternalTileConstructor {
  case object Stage1CoreConstructor extends SodorCoreConstructor {
    def instantiate(implicit conf: SodorConfiguration) = new sodor.stage1.Core()(conf)
  }
  def instantiate(implicit conf: SodorConfiguration) = new SodorInternalTile(Stage1CoreConstructor)
}

case object Stage2Constructor extends SodorInternalTileConstructor {
  case object Stage2CoreConstructor extends SodorCoreConstructor {
    def instantiate(implicit conf: SodorConfiguration) = new sodor.stage2.Core()(conf)
  }
  def instantiate(implicit conf: SodorConfiguration) = new SodorInternalTile(Stage2CoreConstructor)
}

case object Stage3Constructor extends SodorInternalTileConstructor {
  def instantiate(implicit conf: SodorConfiguration) = new SodorInternalTileStage3
}

case object Stage5Constructor extends SodorInternalTileConstructor {
  case object Stage5CoreConstructor extends SodorCoreConstructor {
    def instantiate(implicit conf: SodorConfiguration) = new sodor.stage5.Core()(conf)
  }
  def instantiate(implicit conf: SodorConfiguration) = new SodorInternalTile(Stage5CoreConstructor)
}

case object UCodeConstructor extends SodorInternalTileConstructor {
  case object UCodeCoreConstructor extends SodorCoreConstructor {
    def instantiate(implicit conf: SodorConfiguration) = new sodor.ucode.Core()(conf)
  }
  def instantiate(implicit conf: SodorConfiguration) = new SodorInternalTile(UCodeCoreConstructor)
}

// Key
case object SodorInternalTileKey extends Field[SodorInternalTileConstructor](Stage5Constructor)
