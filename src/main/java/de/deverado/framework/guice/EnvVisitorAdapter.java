package de.deverado.framework.guice;


public class EnvVisitorAdapter implements Environment.EnvVisitor {
    public void dev() {
    }

    @Override
    public void devSupport() {
    }

    public void prod() {
    }

    public void test() {
    }

    @Override
    public void staging() {
    }

    @Override
    public void locStaging() {
    }
}
