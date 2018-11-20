package rl.pacman.ui

import org.scalajs.dom
import org.scalajs.dom.html
import rl.core._
import rl.pacman.core.PacmanProblem._
import rl.pacman.training.QKeyValue

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.util.{Failure, Success}

@JSExportTopLevel("PacmanUI")
object PacmanUI {

  private val env: Environment[GameState, Move]                       = implicitly
  private val stateConversion: StateConversion[GameState, AgentState] = implicitly
  private val agentBehaviour: AgentBehaviour[QLearning[AgentState, Move], AgentState, Move] =
    implicitly

  @JSExport
  def main(document: dom.Document, canvas: html.Canvas, info: html.Div): Unit = {
    loadQ(info) onComplete {
      case Failure(e) =>
        info.innerHTML = "Failed to load Q data."
        println(e)
      case Success(q) =>
        info.innerHTML = "Loaded Q data."

        val initialAgentData: QLearning[AgentState, Move] =
          QLearning(α = 0.1, γ = 0.9, ε = 0.1, Q = q)

        var agentData            = initialAgentData
        var gameState: GameState = initialState
        var episode              = 1

        def step(): Unit = {
          val currentState    = stateConversion.convertState(gameState)
          val possibleActions = env.possibleActions(gameState)
          val (nextAction, updateAgent) =
            agentBehaviour.chooseAction(agentData, currentState, possibleActions)
          val (nextState, reward) = env.step(gameState, nextAction)

          agentData = updateAgent(ActionResult(reward, stateConversion.convertState(nextState)))
          gameState = nextState

          drawGame(canvas, gameState, nextAction)

          if (env.isTerminal(gameState)) {
            episode += 1
            gameState = initialState
          }
        }

        dom.window.setInterval(() => step(), 500)
    }

  }

  private def loadQ(info: html.Div): Future[Map[AgentState, Map[Move, Double]]] = {
    info.innerHTML = "Downloading Q-values JSON file..."

    dom.ext.Ajax.get("Q.json").flatMap { r =>
      info.innerHTML = "Parsing Q-values JSON file..."

      import io.circe.parser._
      val either = for {
        json    <- parse(r.responseText)
        decoded <- json.as[List[QKeyValue]]
      } yield {
        decoded.map(kv => (kv.key, kv.value)).toMap
      }

      Future.fromTry(either.toTry)
    }
  }

  private def drawGame(canvas: html.Canvas, state: GameState, actionTaken: Move): Unit = {
    val ctx         = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    val pixelWidth  = 50
    val pixelHeight = 50

    def drawGhost(ghost: Location, colour: String): Unit = {
      ctx.beginPath()
      ctx.fillStyle = colour
      ctx.arc(ghost.x * pixelWidth + 25, ghost.y * pixelHeight + 25, 20, 0.0, Math.PI * 2.0)
      ctx.fill()
      ctx.closePath()
    }

    ctx.fillStyle = "black"
    ctx.fillRect(0, 0, 1000, 350)

    // draw walls
    for (wall <- walls) {
      ctx.fillStyle = "blue"
      ctx.fillRect(wall.x * pixelWidth + 10,
                   wall.y * pixelHeight + 10,
                   pixelWidth - 20,
                   pixelHeight - 20)
    }

    // draw food
    for (food <- state.food) {
      ctx.beginPath()
      ctx.fillStyle = "yellow"
      ctx.arc(food.x * pixelWidth + 25, food.y * pixelHeight + 25, 5, 0.0, Math.PI * 2.0)
      ctx.fill()
      ctx.closePath()
    }

    // draw pills
    for (pill <- state.pills) {
      ctx.beginPath()
      ctx.fillStyle = "yellow"
      ctx.arc(pill.x * pixelWidth + 25, pill.y * pixelHeight + 25, 15, 0.0, Math.PI * 2.0)
      ctx.fill()
      ctx.closePath()
    }

    // draw ghosts
    state.mode match {
      case Mode.Normal =>
        drawGhost(state.ghost1, "green")
        drawGhost(state.ghost2, "red")
      case Mode.ChaseGhosts(_) =>
        drawGhost(state.ghost1, "cyan")
        drawGhost(state.ghost2, "cyan")
    }

    // draw pacman
    val (startAngle, endAngle) = actionTaken match {
      case Move.Left  => (Math.PI * -0.8, Math.PI * 0.8)
      case Move.Up    => (Math.PI * -0.3, Math.PI * 1.3)
      case Move.Right => (Math.PI * 0.2, Math.PI * 1.8)
      case Move.Down  => (Math.PI * 0.7, Math.PI * 2.3)
    }
    ctx.beginPath()
    ctx.fillStyle = "yellow"
    ctx.arc(state.pacman.x * pixelWidth + 25,
            state.pacman.y * pixelHeight + 25,
            20.0,
            startAngle,
            endAngle)
    ctx.lineTo(state.pacman.x * pixelWidth + 25, state.pacman.y * pixelHeight + 25)
    ctx.closePath()
    ctx.fill()
  }

}
