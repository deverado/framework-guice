package de.deverado.framework.guice;

import org.junit.Test;

import static org.junit.Assert.*;

public class EnvironmentTest {

	@Test
	public void testEnvironment() {
		Environment e = new Environment("DEV");
		final Boolean[] h = new Boolean[] { false, false };
		Environment.EnvVisitor v = new EnvVisitorAdapter() {
			@Override
			public void dev() {
				h[0] = true;
			}

			@Override
			public void test() {
				h[1] = true;
			}

			@Override
			public void prod() {
				h[1] = true;
			}

			@Override
			public void staging() {
				h[1] = true;
			}
		};
		e.accept(v);
		assertTrue(h[0]);
		assertFalse(h[1]);
	}

	@Test
	public void testEnvironmentShouldResolveEmptyToTest() {
		assertEquals(Environment.Code.TEST,
				new Environment(null).getCurrentEnvironment());
	}

	@Test
	public void testEnvironmentRejectsOtherBS() {
		try {
			new Environment("");
			fail();
		} catch (IllegalArgumentException iae) {
			// good
		}
		try {
			new Environment("bsasss");
			fail();
		} catch (IllegalArgumentException iae) {
			// good
		}
	}

}
