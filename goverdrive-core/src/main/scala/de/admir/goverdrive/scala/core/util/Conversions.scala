package de.admir.goverdrive.scala.core.util

import de.admir.goverdrive.java.core.util.Xor

import scala.collection.JavaConversions.asScalaBuffer
import scala.util.Left


object Conversions {
    implicit def xor2Either[L, R](xor: Xor[L, R]): L Either R = xor match {
        case x if x.isLeft => Left(x.getLeft)
        case x if x.isRight => Right(x.getRight)
    }

    implicit def javaList2Seq[T](javaList: java.util.List[T]): Seq[T] = asScalaBuffer(javaList)

    implicit def xorWithList2EitherWithSeq[L, R](xorWithList: Xor[L, java.util.List[R]]): L Either Seq[R]= {
        xor2Either(xorWithList) match {
            case Right(javaList) => Right(javaList2Seq(javaList))
            case Left(leftValue) => Left(leftValue)
        }
    }
}
