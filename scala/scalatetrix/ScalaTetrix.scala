/*
 * ScalaTetrix.scala
 */

package ScalaTetrix

import swing._
import event._

object App extends SimpleSwingApplication {
  import event.Key._
  import java.awt.{Dimension, Graphics2D, Graphics, Image, Rectangle}
  import java.awt.{Color => AWTColor}
  import java.awt.event.{ActionEvent}
  import javax.swing.{Timer => SwingTimer, AbstractAction}

  var game = Game.newGame

  override def top = frame

  val frame = new MainFrame {
    title = "Scala Tetrix"
    contents = mainPanel
    lazy val mainPanel = new Panel() {
      focusable = true
      background = AWTColor.white
      preferredSize = new Dimension(380, 600)

      override def paint(g: Graphics2D) {
        g.setColor(AWTColor.white)
        g.fillRect(0, 0, size.width, size.height)
        onPaint(g)
      }

      listenTo(keys)

      reactions += {
        case KeyPressed(_, key, _, _) =>
          onKeyPress(key)
          repaint()
      }
    } // new Panel()

    val timer = new SwingTimer(1000, new AbstractAction() {
      override def actionPerformed(e: ActionEvent) {
        if (game.mode == ActiveMode) {
          game = game.tick
          repaint()
        }
      }
    })

    timer.start
  } // def top new MainFrame


  def onKeyPress(keyCode: Value) = keyCode match {
    case Left => game = process(_.moveBy(-1, 0))
    case Right => game = process(_.moveBy(1, 0))
    case Up => game = process(_.rotate)
    case Down => game = game.tick
    case Space => game = game.drop
    case _ =>
  }

  def onPaint(g: Graphics2D) {
    val CELL_SIZE: Int = 20
    val CELL_MARGIN: Int = 1
    val darkRed = new AWTColor(200, 100, 100)

    def buildRect(p: Tuple2[Int, Int], board: Board): Rectangle =
      new Rectangle(p._1 * (CELL_SIZE + CELL_MARGIN) + board.pos._1,
        (board.size._2 - p._2 - 1) * (CELL_SIZE + CELL_MARGIN) + board.pos._2,
        CELL_SIZE,
        CELL_SIZE)

    def drawBoard(board: Board) {
      g.setColor(AWTColor.gray)

      board.coordinates.filterNot(board.cells.contains).
        foreach(p => g draw buildRect(p, board))

      board.cells.keys.foreach(g fill buildRect(_, board))
    }

    def drawBlock(block: Block, board: Board) {
      g.setColor(darkRed)
      block.foreach(g fill buildRect(_, board))
    }

    drawBoard(game.board)
    drawBlock(game.block, game.board)
    drawBoard(game.miniBoard)
  }

  def process(f: Game => Option[Game]) =
    f(game) match {
      case Some(e) => e
      case None => game
    }
} // object App

abstract class GameMode
case object NewMode extends GameMode
case object ActiveMode extends GameMode
case object GameOverMode extends GameMode

object Game {
  val BOARD_SIZE = (9, 20)
  val BOARD_POS = (20, 20)
  val MINI_SIZE = (5, 5)
  val MINI_POS = (250, 20)

  def newGame =
    new Game(
      new Board(BOARD_SIZE, BOARD_POS),
      initBlock(Block.randomBlock(), BOARD_SIZE),
      initBlock(Block.randomBlock(), MINI_SIZE),
      NewMode
    )

  def initBlock(block: Block, size: Tuple2[Int, Int]) =
    block.moveTo(size._1 / 2, size._2 - 3)
}

class Game(
  val board: Board,
  val block: Block,
  val nextBlock: Block,
  val mode: GameMode
) {
  val miniBoard =
    new Board(Game.MINI_SIZE, Game.MINI_POS).set(nextBlock)

  def tick: Game = synchronized {
    moveBy(0, -1) match {
      case Some(game) => game
      case None => hitTheFloor
    }
  }

  def hitTheFloor: Game = {
    var newBoard = board.checkRows
    val newBlock = Game.initBlock(nextBlock, Game.BOARD_SIZE)
    val newNextBlock = Game.initBlock(Block.randomBlock(), Game.MINI_SIZE)

    var newMode = mode
    if (!newBoard.isInBound(newBlock)
        || newBoard.isCollide(newBlock)) newMode = GameOverMode
    else newBoard = newBoard + newBlock

    new Game(newBoard, newBlock, newNextBlock, newMode)
  }

  def drop: Game =
    moveBy(0, -1) match {
      case None => this
      case Some(e) => e.drop
    }

  def moveBy(delta: Tuple2[Int, Int]): Option[Game] =
    transform(_.moveBy(delta))

  def rotate: Option[Game] =
    transform(_.rotate(-math.Pi / 2.0))

  def transform(f: Block => Block): Option[Game] = {
    if (mode != ActiveMode && mode != NewMode) return None

    val newMode = if (mode == NewMode) ActiveMode
    else mode

    board.transform(block, f) match {
      case (None, e) => None
      case (Some(newBoard), newBlock) =>
        Some(new Game(newBoard, newBlock, nextBlock, newMode))
    }
  }

}

class Board(
  val size: Tuple2[Int, Int],
  val pos: Tuple2[Int, Int],
  val cells: Map[Tuple2[Int, Int], BlockType]
) {
  def this(size: Tuple2[Int, Int], pos: Tuple2[Int, Int]) = {
    this(size, pos, Map.empty)
  }

  def coordinates =
    for (y <- 0 until size._2; x <- 0 until size._1)
      yield(x, y)

  def clear() =
    new Board(size, pos)

  def transform(
    block: Block,
    f: Block => Block) = {
    val unloadedBoard = this - block
    val transformedBlock = f(block)
    if (!unloadedBoard.isInBound(transformedBlock)
        || unloadedBoard.isCollide(transformedBlock)) (None, block)
    else (Some(unloadedBoard + transformedBlock), transformedBlock)
  }

  def +(block: Block): Board = {
    assert(!isCollide(block) && isInBound(block))

    def loadList(board: Board, xs: List[Tuple2[Int, Int]]): Board = xs match {
      case List() => board
      case x :: tail => loadList(board.set(x, block.blockType), tail)
    }

    loadList(this, block.coordinates)
  }

  private def -(block: Block): Board = {
    assert(isInBound(block))

    def unloadList(board: Board, xs: List[Tuple2[Int, Int]]): Board = xs match {
      case List() => board
      case x :: tail => unloadList(board.unset(x), tail)
    }

    unloadList(this, block.coordinates)
  }

  private def isRowFilled(y: Int): Boolean = {
    val row = for (x <- 0 until size._1)
      yield (x, y)
    row forall (cells.contains(_))
  }

  private def removeRow(y: Int): Board = {
    var newBoard = this
    for (y <- y until size._2 - 1; x <- 0 until size._1) {
      newBoard = if (newBoard.cells.contains((x, y + 1)))
        newBoard.set((x, y), newBoard.cells((x, y + 1)))
      else newBoard.unset((x, y))
    } // x, y

    for (x <- 0 until size._1) {
      newBoard = newBoard.unset((x, size._2 - 1))
    } // x, y

    return newBoard
  }

  def checkRows: Board = {
    var newBoard = this
    for (i <- 0 until size._2) {
      val y = size._2 - 1 - i
      if (newBoard.isRowFilled(y)) {
        newBoard = newBoard.removeRow(y)
      }
    } // i
    return newBoard
  }

  def isCollide(block: Block): Boolean =
    block exists (cells.contains(_))

  def isInBound(block: Block): Boolean =
    block forall (p => p._1 >= 0 && p._1 < size._1
      && p._2 >= 0 && p._2 < size._2)

  def set(block: Block): Board =
    clear + block

  def set(key: Tuple2[Int, Int], value: BlockType) =
    new Board(size, pos, cells + (key -> value))

  def unset(key: Tuple2[Int, Int]) =
    new Board(size, pos, cells - key)
}

sealed abstract class BlockType
case object Tee extends BlockType
case object Bar extends BlockType
case object Box extends BlockType
case object El extends BlockType
case object Jay extends BlockType
case object Es extends BlockType
case object Zee extends BlockType

object Block {
  val blockTypes: List[BlockType] = List(Tee, Bar, Box, El, Jay, Es, Zee)

  def randomBlock(): Block = {
    val blockType = blockTypes(util.Random.nextInt(blockTypes.size));
    new Block(blockType,
      (4, 10),
      blockType match {
        case Tee => List((0.0, 0.0), (-1.0, 0.0), (1.0, 0.0), (0.0, 1.0))
        case Bar => List((0.0, -1.5), (0.0, -0.5), (0.0, 0.5), (0.0, 1.5))
        case Box => List((-0.5, 0.5), (0.5, 0.5), (-0.5, -0.5), (0.5, -0.5))
        case El => List((0.0, 0.0), (0.0, 1.0), (0.0, -1.0), (1.0, -1.0))
        case Jay => List((0.0, 0.0), (0.0, 1.0), (0.0, -1.0), (-1.0, -1.0))
        case Es => List((-0.5, 0.0), (0.5, 0.0), (-0.5, 1.0), (0.5, -1.0))
        case Zee => List((-0.5, 0.0), (0.5, 0.0), (-0.5, -1.0), (0.5, 1.0))
      }
    )
  }
}

class Block(
  val blockType: BlockType,
  val pos: Tuple2[Int, Int],
  val locals: List[Tuple2[Double, Double]]
) extends IndexedSeq[Tuple2[Int, Int]] {
  override def length = coordinates.length
  override def apply(index: Int) = coordinates(index)

  def coordinates: List[Tuple2[Int, Int]] =
    for (p <- locals)
      yield (math.round(p._1 + pos._1).asInstanceOf[Int],
        math.round(p._2 + pos._2).asInstanceOf[Int])

  def moveBy(delta: Tuple2[Int, Int]) =
    moveTo((pos._1 + delta._1, pos._2 + delta._2))

  def moveTo(newPos: Tuple2[Int, Int]) =
    new Block(blockType, newPos, locals)

  def rotate(theta: Double) = {
    val s = math.sin(theta)
    val c = math.cos(theta)
    val newLocals = for (p <- locals)
      yield (roundToHalf(p._1 * c - p._2 * s),
             roundToHalf(p._1 * s + p._2 * c))
    new Block(blockType, pos, newLocals)
  }

  private def roundToHalf(value: Double) =
    math.round(value * 2.0) * 0.5
}

// vim: set ts=4 sw=4 et:
