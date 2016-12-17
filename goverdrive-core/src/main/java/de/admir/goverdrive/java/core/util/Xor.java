package de.admir.goverdrive.java.core.util;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class Xor<L, R> {
    L leftValue;
    R rightValue;

    public static <L, R> Xor<L, R> left(L left) {
        return new Xor.Left<>(left);
    }

    public static <L, R> Xor<L, R> right(R right) {
        return new Xor.Right<>(right);
    }

    public static <R> Xor<Exception, R> catchNonFatal(Callable<R> callable) {
        try {
            return Xor.right(callable.call());
        } catch (Exception e) {
            return Xor.left(e);
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <L, R>  Xor<L, R> fromOptional(Optional<R> optional, Supplier<L> elseValueSupplier) {
        return optional.isPresent() ? Xor.right(optional.get()) : Xor.left(elseValueSupplier.get());
    }

    public abstract <T, U> Xor<T, U> map(Function<L, T> transformLeft, Function<R, U> transformRight);

    public abstract <T, U> Xor<T, U> flatMap(Function<L, Xor<T, U>> transformLeft, Function<R, Xor<T, U>> transformRight);

    public abstract <U> Xor<L, U> flatMapRight(Function<R, Xor<L, U>> transformRight);

    public abstract <T> Xor<T, R> flatMapLeft(Function<L, Xor<T, R>> transformLeft);

    public <U> Xor<L, U> mapRight(Function<R, U> transformRight) {
        return this.map(left -> left, transformRight);
    }

    public <U> Xor<U, R> mapLeft(Function<L, U> transformLeft) {
        return this.map(transformLeft, right -> right);
    }

    public Xor<L, R> unit(Runnable workUnit) {
        workUnit.run();
        return this;
    }

    public abstract L getLeft();

    public abstract R getRight();

    public abstract boolean isLeft();

    public abstract boolean isRight();

    private static class Left<L, R> extends Xor<L, R> {
        private Left(L left) {
            this.leftValue = left;
            this.rightValue = null;
        }

        public L getLeft() {
            return leftValue;
        }

        public R getRight() {
            throw new NoSuchElementException("Tried to getRight from a Left");
        }

        public boolean isLeft() {
            return true;
        }

        public boolean isRight() {
            return false;
        }

        public <T> T fold(Function<L, T> transformLeft, Function<R, T> transformRight) {
            return transformLeft.apply(this.leftValue);
        }

        @Override
        public <T, U> Xor<T, U> map(Function<L, T> transformLeft, Function<R, U> transformRight) {
            return Xor.left(transformLeft.apply(this.leftValue));
        }

        @Override
        public <T, U> Xor<T, U> flatMap(Function<L, Xor<T, U>> transformLeft, Function<R, Xor<T, U>> transformRight) {
            return transformLeft.apply(this.leftValue);
        }

        @Override
        public <T> Xor<T, R> flatMapLeft(Function<L, Xor<T, R>> transformLeft) {
            return transformLeft.apply(leftValue);
        }

        @Override
        public <U> Xor<L, U> flatMapRight(Function<R, Xor<L, U>> transformRight) {
            return Xor.left(leftValue);
        }
    }

    private static class Right<L, R> extends Xor<L, R> {
        private Right(R right) {
            this.leftValue = null;
            this.rightValue = right;
        }

        public L getLeft() {
            throw new NoSuchElementException("Tried to getLeft from a Right");
        }

        public R getRight() {
            return rightValue;
        }

        public boolean isLeft() {
            return false;
        }

        public boolean isRight() {
            return true;
        }

        public <T> T fold(Function<L, T> transformLeft, Function<R, T> transformRight) {
            return transformRight.apply(this.rightValue);
        }

        @Override
        public <T, U> Xor<T, U> map(Function<L, T> transformLeft, Function<R, U> transformRight) {
            return Xor.right(transformRight.apply(this.rightValue));
        }

        @Override
        public <T, U> Xor<T, U> flatMap(Function<L, Xor<T, U>> transformLeft, Function<R, Xor<T, U>> transformRight) {
            return transformRight.apply(this.rightValue);
        }

        @Override
        public <T> Xor<T, R> flatMapLeft(Function<L, Xor<T, R>> transformLeft) {
            return Xor.right(rightValue);
        }

        @Override
        public <U> Xor<L, U> flatMapRight(Function<R, Xor<L, U>> transformRight) {
            return transformRight.apply(rightValue);
        }
    }
}
