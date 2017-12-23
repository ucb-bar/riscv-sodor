// See LICENSE.SiFive for license details.

package uncore.tilelink2

import Chisel._
import chisel3.internal.sourceinfo.SourceInfo
import config._
import diplomacy._
import scala.math.{min,max}

class TLFilter(select: AddressSet)(implicit p: Parameters) extends LazyModule
{
  val node = TLAdapterNode(
    clientFn  = { cp => cp },
    managerFn = { mp =>
      mp.copy(managers = mp.managers.map { m =>
        val filtered = m.address.map(_.intersect(select)).flatten
        val alignment = select.alignment /* alignment 0 means 'select' selected everything */
        val maxTransfer = 1 << 30
        val capTransfer = if (alignment == 0 || alignment > maxTransfer) maxTransfer else alignment.toInt
        val cap = TransferSizes(1, capTransfer)
        if (filtered.isEmpty) { None } else {
          Some(m.copy(
            address            = filtered,
            supportsAcquireT   = m.supportsAcquireT  .intersect(cap),
            supportsAcquireB   = m.supportsAcquireB  .intersect(cap),
            supportsArithmetic = m.supportsArithmetic.intersect(cap),
            supportsLogical    = m.supportsLogical   .intersect(cap),
            supportsGet        = m.supportsGet       .intersect(cap),
            supportsPutFull    = m.supportsPutFull   .intersect(cap),
            supportsPutPartial = m.supportsPutPartial.intersect(cap),
            supportsHint       = m.supportsHint      .intersect(cap)))
        }
      }.flatten)
    })

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val in  = node.bundleIn
      val out = node.bundleOut
    }
    io.out <> io.in
  }
}

object TLFilter
{
  // applied to the TL source node; y.node := TLBuffer(x.node)
  def apply(select: AddressSet)(x: TLOutwardNode)(implicit p: Parameters, sourceInfo: SourceInfo): TLOutwardNode = {
    val filter = LazyModule(new TLFilter(select))
    filter.node := x
    filter.node
  }
}
