clean:
	rm exists
	rm -rf .metals/
	rm -rf .scala/
	rm -rf .bsp/

# We can't use scalafmt --check with scala-cli because of:
# https://github.com/VirtusLab/scala-cli/issues/528
# So we need to wait for a release with that fix.
format:
	cs launch scalafmt -- --check

format-check:
	cs launch scalafmt -- --check

# For now we don't package with a native image because of:
# https://github.com/VirtusLab/scala-cli/pull/527
# So we need to wait for a release with that fix.
package:
	scala-cli package . -o exists -f

test:
	scala-cli test .
