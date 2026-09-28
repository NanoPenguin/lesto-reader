package io.github.nanopenguin.pace.book

/** Built-in text for trying the reader before file support exists. Public domain. */
val SampleBook =
    Book(
        title = "Alice’s Adventures in Wonderland",
        author = "Lewis Carroll",
        blocks =
        listOf(
            Block.Heading("Down the Rabbit-Hole", level = 1),
            Block.Paragraph(
                "Alice was beginning to get very tired of sitting by her sister on the bank, and of " +
                    "having nothing to do: once or twice she had peeped into the book her sister was " +
                    "reading, but it had no pictures or conversations in it, “and what is the use of a " +
                    "book,” thought Alice “without pictures or conversations?”",
            ),
            Block.Paragraph(
                "So she was considering in her own mind (as well as she could, for the hot day made her " +
                    "feel very sleepy and stupid), whether the pleasure of making a daisy-chain would be " +
                    "worth the trouble of getting up and picking the daisies, when suddenly a White Rabbit " +
                    "with pink eyes ran close by her.",
            ),
            Block.Paragraph(
                "There was nothing so very remarkable in that; nor did Alice think it so very much out " +
                    "of the way to hear the Rabbit say to itself, “Oh dear! Oh dear! I shall be late!”",
            ),
            Block.Heading("The Pool of Tears", level = 1),
            Block.Paragraph(
                "“Curiouser and curiouser!” cried Alice (she was so much surprised, that for the moment " +
                    "she quite forgot how to speak good English); “now I’m opening out like the largest " +
                    "telescope that ever was! Good-bye, feet!”",
            ),
        ),
    )
