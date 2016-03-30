package org.misoton.lexer

import scala.util.matching.Regex

object ParserCombinator {

  case class State(input: String, pos: Int)

  case class ParseError(message: String, state: State)

  type ParseResult[T] = Either[ParseError, (T, State)]

  abstract class Parser[+T] extends (State => ParseResult[T]) {

    private var name: String = ""

    def named(name: String): Parser[T] = {
      this.name = name
      this
    }

    override def toString(): String = name

    def ~[R](that: Parser[R]): Parser[(T, R)] = parserGen((in) => {
      this(in) match {
        case Right((a, in2)) => that(in2) match {
          case Right((b, in3)) => Right(((a, b), in3))
          case Left(e) => Left(e)
        }
        case Left(e) => Left(e)
      }
    }).named(this.name + "~" + that.name)

    def ~>[R](that: Parser[R]): Parser[R] = parserGen((in) => {
      (this ~ that)(in) match {
        case Right(((_, a), in2)) => Right((a, in2))
        case Left(e) => Left(e)
      }
    }).named(this.name + "~>" + that.name)

    def <~[R](that: Parser[R]): Parser[T] = parserGen((in) => {
      (this ~ that)(in) match {
        case Right(((a, _), in2)) => Right((a, in2))
        case Left(e) => Left(e)
      }
    }).named(this.name + "<~" + that.name)

    def /[R >: T](that: Parser[R]): Parser[R] = parserGen((in) => {
      this(in) match {
        case r@Right(_) => r
        case Left(_) => that(in) match {
          case r2@Right(_) => r2
          case Left(e) => Left(e)
        }
      }
    }).named(this.name + "~" + that.name)

    def `!`: Parser[Unit] = parserGen((in) => {
      this(in) match {
        case Right((a, s)) => Left(ParseError("Parsing succeed, but excepted to failure: " + a, s))
        case Left(e) => Right((), e.state)
      }
    }).named("!" + this.name)
  }

  def parserGen[T](f: State => ParseResult[T]): Parser[T] =
    new Parser[T] {
      def apply(input: State): ParseResult[T] = f(input)
    }

  def parseAll[T](input: String, parser: Parser[T]): Either[ParseError, T] = {
    parser(State(input, 0)) match {
      case Right((tree, State(left, pos))) => if(left.length == 0) Right(tree) else Left(ParseError("Left some string", State(left, pos)))
      case Left(e) => Left(e)
    }
  }

  implicit def string2parser(str: String): Parser[String] = parserGen((in) => {
    if(in.input startsWith str) Right((str, State(in.input substring str.length, in.pos + str.length)))
    else Left(ParseError(in.input + " cannot match with " + str, in))
  }).named(str)

  implicit def regex2parser(regex: Regex): Parser[String] = parserGen((in) => {
    in.input match {
      case regex(value) => Right((value, State(in.input substring value.length, in.pos + value.length)))
      case _ => Left(ParseError(in.input + " cannot match with " + regex.toString, in))
    }
  }).named(regex.toString)
}
