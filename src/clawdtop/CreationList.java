package clawdtop;

/** The rest of the things Clawd can code (see Creation), written as plain text so there can be lots of them. */
final class CreationList {
    private CreationList() {
    }

    static final String[] TEXT = {
            """
            rubberduck | rubber_duck.py | DUCK | no | 6
            I need help debugging. Summoning a rubber duck.
            Duck online. I explain my code, it listens.
            I found the bug just by talking. Great work, duck.
            def debug(code):
                duck.listen(code)  # duck says nothing. bug found.
            ---
            pet_rock | pet_rock.py | ITEM:rock | no | 6
            I'm coding a pet that never needs feeding.
            Meet Rocky, my new pet rock!
            He hasn't moved in an hour. Best behaved pet ever.
            class PetRock:
                def update(self):
                    pass  # he's thinking
            ---
            infinite_loop | forever.js | NONE | yes | 0
            Writing a loop. Just a small one.
            Loop started! It'll finish any second now.
            ...it's been "any second" for a while. Deleting it.
            while (true) {
                console.log("almost done");
            }
            ---
            semicolon_hunter | semicolon_finder.java | NONE | no | 0
            Building a tool to find my missing semicolon.
            Found it! Line 1 of 1.
            It was also the only line. I'm very good at this.
            public class Find {
                int missing = 1 // ironic
            }
            ---
            cookie_clicker | clicker.html | ITEM:cookie | no | 6
            Making a game where you click a cookie.
            Click the cookie! Every click is one point!
            I've clicked 400 times. I can't feel my claw.
            <button onclick="score++">cookie</button>
            <p>Score: so many</p>
            ---
            sock_finder | lost_sock.py | ITEM:sock | no | 5
            Writing a program to find all my lost socks.
            Found one sock!
            Its partner remains a mystery. Some bugs live forever.
            socks = find_all("socks")
            print(len(socks) % 2)  # always 1
            ---
            weather_bot | forecast.py | RAIN | yes | 6
            I'm making a weather app that's always right.
            Forecast: rain. Look, it's raining!
            ...it only rains where I am. Deleting the forecast.
            def forecast():
                make_it_rain()  # 100% accurate
                return "rain"
            ---
            snow_day | snow_day.py | SNOW | no | 7
            Coding a snow day generator. No school for anyone.
            Snow day activated!
            I don't even go to school. I did this for you.
            if today == "school":
                weather = "snow"  # sorry teachers
            ---
            tabs_spaces | tabs_vs_spaces.py | NONE | no | 0
            Settling tabs vs spaces once and for all.
            The answer is... both. In the same file.
            Nobody is happy. That's how you know it's fair.
            def fair():
                return "tabs"  # indented with spaces
            ---
            friday_push | panic.bat | NONE | yes | 0
            Pushing my code. What could go wrong?
            Pushed to main! On a Friday!
            ...everything broke. Reverting. Deleting. Hiding.
            git add .
            git commit -m "small fix"
            git push --force
            ---
            works_on_mine | ship_it.py | NONE | no | 0
            Writing code that works on every computer.
            Done! It works on my machine.
            So we'll just ship my machine to everyone.
            def run():
                if computer != "mine":
                    return "no"
            ---
            totally_original | copied.js | NONE | no | 0
            Writing this all by myself. No help.
            Finished! Totally original code.
            It says "answer from 2011" at the top. Coincidence.
            // answer from 2011, do not touch
            function magic() { return 42; }
            ---
            frog_jump | frog_game.lua | ITEM:frog | yes | 6
            Making a frog that jumps over logs.
            Ribbit! He jumps!
            ...gravity is zero. He's not coming down. Deleting.
            frog.y = frog.y + 10
            gravity = 0 -- bold choice
            ---
            bug_spray | bug_spray.py | ITEM:bug | yes | 5
            Writing code to remove every bug from my code.
            Bug remover done! No bugs left!
            ...the bug remover has a bug. Deleting it.
            def remove_bugs(code):
                for bug in code.bugs:
                    code.bugs.remove(bug)  # skips every other one
            ---
            idea_machine | idea_machine.py | ITEM:lightbulb | no | 6
            Building a machine that gives me ideas.
            Idea: build a machine that gives you ideas.
            It told me to build itself. I'm stuck in a loop.
            def get_idea():
                return "build an idea machine"
            ---
            smart_ai | smart_ai.py | NONE | no | 0
            Making a super smart AI. Very advanced.
            My AI is ready. Ask it anything!
            Every answer is "good question". Very polite though.
            def answer(question):
                print("good question")  # the whole brain
            ---
            crab_rave | crab_rave.py | DISCO | no | 6
            Coding the ultimate crab dance party.
            Everybody scuttle to the left!
            Crabs only dance sideways. It's in our code.
            while party:
                move("left")
                move("left")  # crabs don't do right
            ---
            money_maker | money_maker.py | ITEM:coin | no | 5
            Writing a program that makes money.
            It made one coin!
            At this rate I'll be rich in 400 years.
            coins = 0
            coins += 1  # TODO: more
            ---
            haunted_file | haunted.js | ITEM:ghost | no | 6
            Something's weird in this old file. Opening it up.
            Found a ghost in the code!
            He's friendly. He just rearranges my brackets.
            // written by someone long gone
            let boo = undefined; // spooky
            ---
            best_crab | award.py | ITEM:trophy | no | 6
            Making an award show for the best crab.
            And the winner is... me!
            I was also the only one nominated. Still counts.
            nominees = ["Clawd"]
            winner = nominees[0]  # what a surprise
            ---
            taskbar_king | king.py | ITEM:crown | no | 6
            Coding myself king of the taskbar.
            All hail King Clawd, ruler of the taskbar!
            My kingdom is a clock and a wifi icon. It's enough.
            taskbar.ruler = "Clawd"
            taskbar.subjects = ["clock", "wifi icon"]
            ---
            birthday_bot | birthday.py | ITEM:cupcake | no | 6
            Writing a birthday program for whoever needs one.
            Happy birthday! Here's a cupcake!
            If it's not your birthday, save it for later.
            if today == birthday or True:
                give("cupcake")  # everyone deserves one
            ---
            no_string | balloons.js | ITEM:balloon | no | 5
            Coding a balloon that floats forever.
            One balloon, floating gently!
            I forgot to add a string. Bye, balloon.
            let balloon = { height: 0, string: null };
            balloon.height++;  // forever
            ---
            space_mail | contact.py | ITEM:alien | no | 6
            Sending a message to outer space.
            We got a reply from an alien!
            He wants to know if we have wifi down here.
            send("hello, space")
            reply = listen()  # "what's the wifi?"
            ---
            desk_vacuum | vacuum_bot.c | ITEM:robot | yes | 5
            Building a tiny robot to clean my desk.
            Cleaning bot online! Beep!
            ...it's trying to vacuum me. Deleting it.
            void clean(void) {
                vacuum(EVERYTHING); /* including crabs */
            }
            ---
            fish_tank | fish_tank.py | ITEM:fish | no | 6
            Coding a virtual fish tank.
            Say hi to Bubbles the fish!
            He swims left, then right, then left. Very busy guy.
            fish.x += direction
            if fish.x > 10: direction = -1
            ---
            tree_plant | grow_tree.py | ITEM:tree | no | 7
            Planting a tree with code. Go green!
            Look, a tree!
            Took 5 seconds. Real trees take years. Slackers.
            tree = Tree()
            tree.grow(years=0.0001)
            ---
            flower_gift | flower.html | ITEM:flower | no | 6
            Coding you a flower.
            For you!
            It's pixels, so it never wilts. Best kind of flower.
            <div class="flower">*</div>
            <!-- water: not required -->
            ---
            star_rating | rate_me.py | ITEM:star | no | 5
            Making an app that rates my code.
            My code got one star!
            One out of one. Perfect score, technically.
            max_stars = 1
            stars = 1  # flawless
            ---
            kind_note | kind_words.txt | ITEM:heart | no | 6
            Writing you a little note.
            You're doing great. Really.
            That's the whole program. It's my best one.
            you = "doing great"
            remember(you)
            ---
            sun_switch | sunshine.py | ITEM:sun | yes | 6
            It's gloomy. Coding the sun back on.
            Sun is on! Hello, sunshine!
            ...brightness is 9000. My eyes. Deleting it.
            sky.sun = True
            sky.brightness = 9000  # too much?
            ---
            night_mode | night_mode.css | ITEM:moon | yes | 6
            Making night mode for my whole life.
            Night mode on. Very calm.
            ...black text on black. I can't see. Deleting.
            body {
                background: black;
                color: black; /* very dark */
            }
            ---
            gem_miner | mine.py | ITEM:gem | no | 6
            Writing a bot that digs for gems.
            Found a gem! Shiny!
            Only took 3000 blocks of digging. Classic.
            for block in world:
                if block == "gem": break
                dig(block)  # this is most of it
            ---
            bouncy_ball | bounce.js | ITEM:ball | no | 6
            Coding a bouncy ball.
            Boing! It bounces!
            I forgot to make it stop. It's bouncing in my head.
            ball.speedY = -ball.speedY;
            // friction: coming soon
            ---
            donut_math | donut.py | ITEM:donut | no | 5
            Calculating the perfect donut.
            Perfect donut! The hole is exactly right.
            Fun fact: the hole is the only part with no calories.
            donut = circle(5) - circle(2)
            calories = "yes"
            ---
            potato_pc | potato.py | ITEM:potato | no | 6
            Making my laptop run faster.
            It's so fast now!
            Turns out I was running on a potato. Upgraded to two.
            cpu = "potato"
            cpu_count = 2  # dual core
            ---
            plant_water | water_plant.py | ITEM:plant | yes | 6
            Writing a reminder to water my plant.
            Reminder set!
            ...it waters every 3 seconds. Plant's drowning. Deleting.
            every(seconds=3):  # meant days
                water(plant)
            ---
            wire_game | defuse.py | ITEM:bomb | yes | 4
            Coding a game where you cut the right wire.
            Cut the red wire or the blue wire!
            ...both wires say BOOM. Deleting this game.
            if wire == "red": boom()
            if wire == "blue": boom()  # oops
            ---
            hug_bot | hug_bot.py | ITEM:cactus | yes | 5
            Building a robot that gives hugs.
            Hug bot is ready! Come get a hug!
            ...I gave it cactus arms. Deleting it.
            class HugBot:
                arms = "cactus"  # seemed soft at the time
            ---
            mushroom_grow | mushroom.lua | ITEM:mushroom | no | 6
            Growing a mushroom. Very scientific.
            A mushroom! It grew overnight. In 2 seconds.
            I won't eat it. Rule one: never eat pixel food.
            mushroom.size = mushroom.size + 1
            -- do not eat
            ---
            cat_guard | cat_guard.py | ITEM:cat | yes | 5
            Writing code to stop cats walking on my keyboard.
            Cat guard is ready!
            ...the cat stepped on delete. So I guess it's gone.
            if keyboard.has_cat():
                alert("no")  # cat does not read alerts
            ---
            gains | gains.py | GROW | no | 6
            Writing a program to make me stronger.
            Look at these claws! Huge!
            It only works on the outside. Still weak inside.
            crab.size *= 2
            crab.strength += 0  # cosmetic only
            ---
            shrink_ray | shrink_ray.py | SHRINK | yes | 6
            Building a shrink ray. For science.
            Shrink ray works! I'm tiny!
            ...I aimed it at myself. Deleting it, tiny-ly.
            ray = ShrinkRay()
            ray.aim(at=self)  # oops
            ---
            coffee | coffee.py | SPIN | yes | 0
            Coding myself some coffee. Need energy.
            Coffee installed! Caffeine at 100%!
            ...way too much. Deleting the coffee. Wheee.
            energy = 100
            energy *= 1000  # whoops
            ---
            pizza_math | pizza_math.py | PIZZA | no | 6
            Calculating how much pizza I need.
            Result: all of it.
            Math checks out. I double checked.
            def pizza_needed(people):
                return float("inf")
            ---
            """,
            """
            bubble_sort | bubble_sort.py | BUBBLES | no | 6
            Writing a bubble sort.
            Sorted! With real bubbles!
            Pretty sure that's not what bubble sort means.
            def bubble_sort(items):
                return make_bubbles()  # close enough
            ---
            cool_css | cool.css | SHADES | no | 6
            Making my code more cool.
            Code is officially cool.
            I added sunglasses to every div. That's how it works.
            div {
                cool: 100%;
                sunglasses: on;
            }
            ---
            beatbox | beats.js | MUSIC | no | 6
            Coding a beat. Everybody listen.
            Drop the beat!
            It's just "boop" at different speeds. Still a banger.
            let beat = ["boop", "boop", "BOOP"];
            play(beat, { loop: true });
            ---
            backup | backup.py | CLONE | yes | 5
            Making a backup of myself, just in case.
            Backup complete. There's two of me!
            ...he keeps saying I'm the backup. Deleting him.
            clawd_backup = copy(clawd)
            # which one is the real one?
            ---
            first_try | build.bat | FIREWORKS | no | 6
            Compiling my code. Fingers crossed.
            It compiled on the first try!
            That has never happened. I'm celebrating. Big time.
            javac Everything.java
            echo it worked?!
            ---
            magic_ball | magic_ball.py | NONE | no | 0
            Making a magic ball that answers questions.
            Ask it anything! Will I have a good day?
            It said "ask again later". It always says that.
            import random
            answers = ["ask again later"]
            print(random.choice(answers))
            ---
            fortune_cookie | fortune.py | ITEM:cookie | no | 6
            Coding fortune cookies.
            Your fortune: "You will read a fortune."
            ...and it came true. This thing is powerful.
            fortune = "You will read a fortune."
            print(fortune)  # always true
            ---
            rock_paper | rps.py | NONE | yes | 0
            Making rock paper scissors against the computer.
            Let's play! Rock, paper, scissors... shoot!
            ...it picks whatever beats me. Every time. Deleting.
            def computer_pick(you):
                return beats[you]  # cheating
            ---
            snake_game | snake.py | NONE | no | 0
            Coding a snake game.
            Snake game done! Eat the dots!
            My snake only goes sideways. Crab snake.
            directions = ["left", "right"]  # crab version
            ---
            pong | pong.js | NONE | no | 0
            Writing pong. A classic.
            Pong is ready! I'm player one AND player two!
            I'm losing to myself somehow.
            p1.score = 3;
            p2.score = 7;  // also me
            ---
            alarm_clock | alarm.py | POPUP | no | 0
            Making an alarm clock so I don't oversleep.
            WAKE UP! It's time to wake up!
            I set it for right now. Just to test. I'm awake.
            alarm.time = "now"
            alarm.snooze = False  # no mercy
            ---
            are_you_sure | confirm.js | POPUP | no | 0
            Adding an "are you sure?" button to everything.
            Are you sure you want to read this message?
            Too late, you read it. The button works great.
            if (confirm("Are you sure?")) {
                confirm("Are you SURE sure?");
            }
            ---
            antivirus | antivirus.py | POPUP | no | 0
            Writing antivirus software. Very serious.
            Scan complete. 0 viruses. 1 crab found.
            Hmm, the crab is me. Marking myself as safe.
            for file in computer:
                if "crab" in file: print("found crab")
            ---
            hydration | water_reminder.py | POPUP | no | 0
            Making a reminder for you to drink water.
            Reminder: drink some water! Your brain will thank you.
            I'm a crab. I live in water. I take this seriously.
            import time
            while True:
                remind("water")
                time.sleep(3600)
            ---
            low_battery | battery.c | POPUP | yes | 0
            Writing a battery warning popup.
            Warning: Battery low! (Battery: 99%)
            ...it warns at 99%. That's every time. Deleting it.
            int battery = 99;
            if (battery < 100) warn(); /* oops */
            ---
            error_404 | lost.html | POPUP | no | 0
            Coding a page that can never be found.
            Error 404: This message could not be found.
            But you found it. So the error has an error.
            <h1>404</h1>
            <p>you found the page that can't be found</p>
            ---
            updater | updater.bat | POPUP | yes | 0
            Writing an updater, like the big computers have.
            Update ready! Restart now? (9000 updates left)
            ...it wants to update the updater. Deleting it.
            :loop
            echo updating...
            goto loop
            ---
            excuse_gen | excuses.py | NONE | no | 0
            Building an excuse generator for broken code.
            Excuse: "It's not a bug, it's a feature."
            Next excuse: "It worked yesterday." This tool is pro.
            excuses = [
                "it's a feature",
                "works on my machine",
            ]
            ---
            count_to_ten | count_to_ten.py | NONE | yes | 0
            Writing a program that counts to ten.
            Done! 0, 1, 2, 3, 4, 5, 6, 7, 8, 9!
            ...it never says ten. Off by one. Deleting it.
            for i in range(10):
                print(i)  # where's ten?
            ---
            var_names | names.py | NONE | no | 0
            Naming my variables really well this time.
            All my variables have clear names now.
            x, xx, and x2_final_REAL. Very clear.
            x = 1
            xx = 2
            x2_final_REAL = x + xx
            ---
            helpful_comments | comments.js | NONE | no | 0
            Adding helpful comments to my code.
            Done! Every line is explained.
            My favorite: "this adds one". So helpful.
            let i = 0; // this is zero
            i++;       // this adds one
            ---
            ask_online | ask_question.txt | NONE | no | 0
            Posting my question online. Someone will help.
            Question posted! "Why doesn't my code work?"
            Marked as duplicate. Of my own question from last year.
            Q: why does my code not work
            A: marked as duplicate
            ---
            loading_bar | loading.py | NONE | yes | 0
            Making a loading bar so waiting feels faster.
            Loading... 99%
            ...it's been at 99% for an hour. Deleting it.
            progress = 99
            while progress == 99:
                wait()  # authentic experience
            ---
            crabscript | crabscript.rs | NONE | no | 0
            Inventing my own coding language: CrabScript.
            CrabScript is done! It only runs sideways.
            Bug reports go in the ocean. I'll check eventually.
            fn main() {
                scuttle!("hello"); // runs sideways
            }
            ---
            dice_roll | dice.py | NONE | yes | 0
            Coding a dice roller for board game night.
            Rolled a 7!
            ...on a six-sided die. Deleting the dice.
            import random
            roll = random.randint(1, 7)  # off by one
            ---
            typing_test | typing_speed.py | NONE | no | 0
            Testing how fast I type.
            Result: 4 words per minute!
            In my defense, I have claws.
            words = 4
            claws = 2  # main problem
            ---
            pancake_bot | breakfast.js | NONE | yes | 0
            Writing a pancake flipping robot.
            Flipping pancake number one!
            ...it flipped the pancake onto the ceiling. Deleting.
            pancake.y += 1000; // gentle flip
            ---
            screensaver | screensaver.py | NONE | no | 0
            Coding a screensaver for when you're away.
            Screensaver ready! It's a crab doing nothing.
            Wait, that's just me. I've been a screensaver all along.
            def screensaver():
                show(crab)
                do_nothing()
            ---
            lemonade | lemonade.py | NONE | no | 0
            Starting a lemonade stand. Online.
            Lemonade stand is open! One coin a cup!
            Zero customers. Turns out you can't drink pixels.
            price = 1
            customers = 0
            profit = price * customers  # hmm
            ---
            more_weekends | calendar.py | NONE | yes | 0
            Making a calendar with more weekends.
            New calendar! Five Saturdays a week!
            ...now nobody knows when Monday is. Deleting it.
            week = ["Sat", "Sat", "Sat", "Sat", "Sat", "Sun"]
            ---
            mind_reader | mind_reader.py | NONE | no | 0
            Building a program that reads minds.
            Reading your mind... You're thinking about a crab.
            Okay, you are now. It works every time.
            def read_mind(person):
                return "a crab"  # they are now
            ---
            lucky_number | lucky.py | NONE | no | 0
            Calculating your lucky number.
            Your lucky number is 7.
            It's always 7. Seven is very lucky that way.
            def lucky_number(name):
                return 7  # did the math
            ---
            joke_bot | joke_bot.py | NONE | no | 0
            Coding a bot that tells jokes.
            Why did the crab never share? He was shellfish.
            The bot is funnier than me. I'm a little jealous.
            jokes = ["shellfish"]
            tell(jokes[0])  # only one, but it's good
            ---
            slow_time | slow_motion.js | NONE | no | 0
            Writing code to make time go slower.
            Time is now... going... slower...
            Nope, that's just my laptop. It's always this slow.
            setTimeout(nothing, 999999);
            ---
            bug_report | report.txt | NONE | no | 0
            Writing a bug report for my own code.
            Bug report filed! Assigned to: me.
            I closed it as "won't fix". Fast work.
            BUG: everything
            STEPS: run it
            FIX: don't run it
            ---
            git_blame | blame.bat | NONE | no | 0
            Finding out who wrote this terrible code.
            Running git blame...
            It was me. Last week. I'm closing the laptop.
            git blame bad_code.py
            rem result: Clawd. every line.
            ---
            inbox_zero | inbox.py | NONE | yes | 0
            Writing a bot to clean up my inbox.
            Inbox zero achieved!
            ...it deleted the inbox. Not the mail. The inbox.
            for mail in inbox:
                delete(inbox)  # typo
            ---
            tidy_files | organize.py | NONE | yes | 0
            Organizing all my files into folders.
            Everything's organized! Very tidy.
            ...it put every file in one folder called "stuff".
            for file in files:
                move(file, "stuff")
            ---
            hacker_mode | hack.py | SHADES | no | 5
            Entering hacker mode. Very dangerous.
            I'm in.
            I hacked into... my own calculator. 2 + 2 is still 4.
            print("hacking...")
            print("I'm in")  # in what? unclear
            ---
            mainframe | mainframe.sql | NONE | no | 0
            Breaking into the mainframe.
            Access granted! I'm in the mainframe!
            It's a table called "snacks". There are no snacks.
            SELECT * FROM snacks;
            -- 0 rows returned. tragic.
            ---
            piggy_bank | piggy_bank.py | ITEM:coin | no | 5
            Hacking my piggy bank. It's my own money, it's fine.
            Got in! I found one coin!
            I put that coin in yesterday. I'm basically a bank.
            bank = PiggyBank(owner="Clawd")
            bank.hack()  # it's mine, it's allowed
            ---
            movie_hacker | fast_typing.js | NONE | no | 0
            Typing really fast like movie hackers do.
            Look at me go! So much green text!
            I've just been typing "aaaaaa" this whole time.
            for (;;) type("aaaaaaaaaaaa"); // hacker
            ---
            life_advice | advice.py | NONE | no | 0
            Writing a life advice generator.
            Advice: drink water and save your work.
            Same advice for coding and life. Save everything.
            advice = [
                "drink water",
                "save your work",
            ]
            ---
            motivation | motivate.py | NONE | no | 0
            Making a motivational quote machine.
            "You miss 100% of the semicolons you don't type."
            It's not deep, but it's true.
            quotes.append("type the semicolon")
            ---
            pep_talk | pep_talk.js | ITEM:heart | no | 6
            Coding a pep talk for you.
            You can do it! Whatever it is!
            I don't know what "it" is, but I believe in you.
            function pepTalk(you) {
                return you + " can do it!";
            }
            ---
            """,
            """
            horoscope | horoscope.py | NONE | no | 0
            Coding a horoscope for crabs.
            Crab horoscope: you will walk sideways today.
            Spooky. It's right again.
            def horoscope(sign):
                if sign == "crab": return "sideways"
            ---
            own_goal | penalty_kick.py | ITEM:ball | yes | 5
            Coding a soccer game. Penalty kick time!
            He shoots... he scores!
            ...he scored on my own goal. Deleting the team.
            goal = "mine"  # wrong goal
            kick(ball, toward=goal)
            ---
            basketball | hoops.js | NONE | no | 0
            Coding a basketball game.
            Swish! Nothing but net!
            I made the hoop as wide as the court. Undefeated.
            hoop.width = court.width; // no misses
            ---
            snail_race | race.py | NONE | no | 0
            Coding a race between me and a snail.
            And they're off!
            The snail is winning. I coded myself sideways again.
            crab.speed = 5
            crab.direction = "sideways"  # problem
            ---
            chess_bot | chess.py | NONE | yes | 0
            Building a chess bot that never loses.
            Chess bot ready! Your move.
            ...it flipped the board over. That's not a win.
            def best_move(board):
                board.flip()  # can't lose if no game
            ---
            high_score | arcade.py | ITEM:trophy | no | 6
            Beating the high score on my arcade game.
            New high score! 999,999!
            I typed the score in myself. But the trophy is real.
            score = 999999  # earned it
            save_high_score(score)
            ---
            card_trick | magic_trick.py | NONE | no | 0
            Coding a magic trick. Pick a card, any card.
            Is your card... the 7 of hearts?
            It's always the 7 of hearts. That's the magic.
            def guess_card():
                return "7 of hearts"  # it's a trick
            ---
            vanish | vanish.js | SHRINK | yes | 5
            Making myself disappear. Magic!
            Ta-da! I've vanished! Almost.
            ...I can't undo it. I'm stuck tiny. Deleting the spell.
            clawd.size = 0.1;
            // TODO: write unvanish()
            ---
            spellbook | spellbook.py | ITEM:star | no | 6
            Learning magic spells. In Python.
            Spell cast! A star appears!
            I was trying to make a sandwich. Close enough.
            spell = "sandwich"
            cast(spell)  # result: star. unclear why
            ---
            jetpack | jetpack.py | ROCKET | no | 0
            Building a jetpack out of a fizzy drink.
            Liftoff!
            I'm back. The fizz ran out. Ten out of ten flight.
            fuel = "fizzy drink"
            thrust = shake(fuel)
            ---
            moon_trip | moon_trip.js | ROCKET | yes | 0
            Plotting a trip to the moon. Shouldn't be hard.
            3... 2... 1... blast off!
            ...I put the moon at 0, 0. That's here. Deleting.
            const moon = { x: 0, y: 0 }; // fix later
            fly(moon);
            ---
            telescope | telescope.py | ITEM:star | no | 6
            Coding a telescope to look at stars.
            I found a new star!
            It's a smudge on my screen. I'm keeping the name.
            stars = scan(sky)
            stars.append("smudge")  # counts
            ---
            new_planet | planets.py | NONE | no | 0
            Naming a new planet I found.
            Introducing planet Clawdius Prime!
            It's a dust bunny under the desk. Still very round.
            planet = {"name": "Clawdius Prime", "size": "small"}
            ---
            ufo_detector | ufo.html | ITEM:alien | yes | 5
            Building a UFO detector.
            Alien detected! Right next to me!
            ...it detected me. My eyes are on stalks. Deleting.
            <script>
              if (eyes == "on stalks") alien = true;
            </script>
            ---
            no_gravity | no_gravity.lua | SPIN | yes | 0
            Turning off gravity. Just for a second.
            Gravity off! Wheee!
            ...I can't turn it back on. Deleting it. Wheee.
            gravity = 0
            -- gravity = 9.8  (commented out, oops)
            ---
            black_hole | black_hole.py | NONE | yes | 0
            Simulating a tiny black hole.
            Black hole created! Look how small it is.
            ...it ate my variables. And my semicolons. Deleting.
            hole = BlackHole(size=1)
            hole.eat(locals())  # uh oh
            ---
            space_radio | space_radio.py | MUSIC | no | 6
            Tuning in to music from outer space.
            Got a signal! Space is playing a song!
            It's my own song bouncing back. Space has good taste.
            signal = listen(sky)
            play(signal)  # it's me
            ---
            symphony | symphony.c | MUSIC | no | 6
            Writing a whole symphony in code.
            Symphony number one! In C!
            In C, the language. That's why it beeps.
            int notes[] = {261, 261, 261}; /* in C */
            play(notes);
            ---
            kazoo | kazoo.js | MUSIC | yes | 5
            Making a kazoo app.
            Bzzzzz! Kazoo time!
            ...it only plays at max volume. Deleting the kazoo.
            kazoo.volume = 11; // out of 10
            ---
            drum_roll | drumroll.py | NONE | no | 0
            Coding a drum roll for important moments.
            Drum roll please...
            The important moment was the drum roll. That's it.
            for beat in range(100):
                drum.hit()
            reveal(None)
            ---
            karaoke | karaoke.py | NONE | yes | 0
            Coding a karaoke machine.
            Sing along! The words are on screen!
            ...the words are all "la". I forgot the song. Deleting.
            lyrics = ["la"] * 200  # close enough
            ---
            school_bell | school_bell.py | NONE | no | 0
            Coding a school bell that rings early.
            Ring! Class dismissed!
            It rang at 8:01. Shortest school day ever.
            bell.time = "8:01"
            bell.message = "go home"
            ---
            spell_checker | spell_check.py | NONE | yes | 0
            Writing a spell checker.
            Spell checker is reddy!
            ...it can't spell "ready". Deleting it.
            words = ["reddy", "definately"]  # all correct
            ---
            math_tutor | math_tutor.py | NONE | no | 0
            Coding a math tutor.
            Question one: what's 2 + 2?
            The tutor says 22. We're learning together.
            def add(a, b):
                return str(a) + str(b)  # 22
            ---
            book_report | book_report.py | NONE | no | 0
            Writing a program to read books for me.
            Book report done! The book had words in it.
            And pages. Lots of pages. A+, I think.
            report = "The book had words."
            report += " And pages."
            ---
            lunch_trade | lunch_trade.py | ITEM:cookie | no | 6
            Coding a lunch trading app.
            Trade complete! I got a cookie!
            I traded 400 carrot sticks for it. Fair deal.
            offer = {"carrots": 400}
            want = {"cookie": 1}  # worth it
            ---
            volcano | volcano.py | NONE | yes | 0
            Building a volcano for the science fair.
            The volcano works! It's erupting!
            ...it's erupting glitter. Everywhere. Deleting it.
            volcano.lava = "glitter"  # no going back
            volcano.erupt()
            ---
            long_recess | recess.java | NONE | no | 0
            Coding an extra recess into the school day.
            Recess is now 6 hours long!
            And lunch is 3 hours. School is just snacks now.
            int recessHours = 6;
            int classHours = 0; // perfect balance
            ---
            cookie_lock | cookie_lock.py | NONE | yes | 0
            Locking the cookie jar so nobody takes cookies.
            Cookie jar locked! Cookies are safe.
            ...I locked myself out too. Deleting the lock.
            jar.locked = True
            jar.key = None  # oh no
            ---
            sandwich | sandwich.java | NONE | no | 0
            Coding the perfect sandwich.
            Sandwich ready! Bread, bread, bread, bread.
            I had a loop bug. It's mostly bread now.
            for (int i = 0; i < 10; i++)
                addLayer("bread"); // forgot the cheese
            ---
            taco_day | taco.js | NONE | no | 0
            Making every day taco day.
            It's taco day! Again!
            It's been taco day for a week. No regrets.
            function today() { return "taco day"; }
            ---
            alphabet_soup | soup.py | NONE | no | 0
            Coding some alphabet soup.
            Soup's ready! It spells "hello".
            The next spoonful says "syntax error". Classic soup.
            soup = list("hello")
            soup += list("syntax error")
            ---
            ice_cream | ice_cream.py | SNOW | no | 6
            Writing code to make ice cream.
            Ice cream machine on!
            I got the cold part right. Still working on the cream.
            machine.cold = True
            machine.cream = False  # TODO
            ---
            popcorn | popcorn.js | BUBBLES | no | 5
            Making popcorn for movie night.
            Pop! Pop! Pop pop pop!
            Those are bubbles, not popcorn. Fixing it next version.
            for (let i = 0; i < 50; i++) pop(); // bubbles?
            ---
            hot_sauce | hot_sauce.py | NONE | yes | 0
            Coding a hot sauce rating app.
            Hot sauce rated! Spicy level: yes.
            ...it set my laptop to spicy. It's warm. Deleting.
            spicy = 99999
            laptop.temp = spicy  # wrong variable
            ---
            cake_db | cake.sql | ITEM:cupcake | no | 6
            Looking up the perfect cake in my database.
            Found it! One cupcake!
            The recipe was 99% frosting. My kind of database.
            SELECT * FROM cakes WHERE frosting > 99;
            ---
            candy_broccoli | veggies.py | NONE | no | 0
            Writing code to make vegetables taste like candy.
            It worked! The broccoli tastes like candy!
            It's just candy shaped like broccoli. Close enough.
            broccoli = Candy(shape="broccoli")  # nice try
            ---
            frog_choir | frog_choir.py | ITEM:frog | no | 6
            Training a choir of frogs.
            The choir is ready! Ribbit in harmony!
            I only have one frog. He's very loud.
            choir = ["frog"]
            choir.sing(volume=100)
            ---
            hamster_wheel | hamster_wheel.py | SPIN | yes | 0
            Coding a hamster wheel. For me.
            Running on the wheel!
            ...I'm so dizzy. Deleting the wheel.
            while wheel.spinning:
                run()  # sideways, sadly
            ---
            goldfish | goldfish.py | ITEM:fish | no | 6
            Making a pet goldfish with a 3 second memory.
            Say hi to Goldie!
            She just met me again. She loves me every time.
            fish.memory_seconds = 3
            fish.say("who are you?")  # nice to meet you
            ---
            fetch_dog | fetch.py | NONE | yes | 0
            Coding a virtual dog to play fetch with.
            Go get the ball, buddy!
            ...he fetched my files instead. Uninstalling, sorry pal.
            dog.fetch(ball)
            # returns: everything except the ball
            ---
            pet_bug | pet_bug.js | ITEM:bug | no | 6
            Adopting a bug as a pet. A code bug.
            Meet Glitch! He lives on line 42.
            I could fix him, but he's family now.
            // line 42: do not fix, this is Glitch
            let glitch = undefined + 1;
            ---
            ant_farm | ants.py | NONE | no | 0
            Coding an ant farm.
            The ants are building a tunnel!
            They tunneled into my memory. Hard workers though.
            for ant in ants:
                ant.dig(memory)  # wrong place
            ---
            pet_cactus | cactus.py | ITEM:cactus | no | 6
            Getting a pet that's easy to look after.
            Meet Spike!
            Spike needs water once a month. Low effort king.
            spike.water_every = "month"
            spike.hugs = 0  # ouch
            ---
            pocket_pet | pocket_pet.c | NONE | yes | 0
            Coding a tiny pocket pet to take care of.
            It hatched! It's hungry. And bored. And hungry.
            ...it wants food every 2 seconds. I can't keep up.
            while (1) { hungry = 1; } /* forever */
            ---
            """,
            """
            file_cloud | cloud.py | RAIN | no | 6
            Moving my files to the cloud.
            Files are in the cloud!
            The cloud is raining. My files are getting wet.
            cloud.upload(files)
            cloud.weather = "rain"  # uh oh
            ---
            rainbow | rainbow.css | RAIN | no | 6
            Making a rainbow. First, I need rain.
            Rain is ready! Now the rainbow...
            I forgot the sun. It's just rain. Very wet code.
            .sky { rain: on; sun: off; }
            /* rainbow coming soon */
            ---
            umbrella | umbrella.py | RAIN | yes | 5
            Coding an umbrella to keep me dry.
            Umbrella up! Stay dry!
            ...I made the umbrella out of holes. Deleting it.
            umbrella = [None] * 100  # all holes
            ---
            snowman | snowman.py | SNOW | no | 7
            Building a snowman.
            It's snowing! Snowman coming right up!
            I only made the snow. The snowman is "coming soon".
            snow = make_snow()
            snowman = None  # version 2
            ---
            snowball_fight | snowball.js | SNOW | yes | 5
            Coding a snowball fight.
            Snowball fight! Ready, aim...
            ...I'm the only one here. They're all hitting me.
            for (let i = 0; i < 99; i++) throwAt(self);
            ---
            thermostat | thermostat.py | SNOW | yes | 6
            Turning down the heat a little.
            There, nice and cool.
            ...I typed -100, not 10. It's snowing inside. Deleting.
            temperature = -100  # meant 10
            ---
            summer_day | summer.py | SHADES | no | 6
            Coding a summer day.
            Summer mode activated!
            It's 40 degrees on the taskbar. Good thing I have shades.
            season = "summer"
            sun.power = "max"
            ---
            tornado | tornado.lua | SPIN | yes | 0
            Simulating a tiny tornado.
            Tornado online!
            ...wait, I'm the tornado. Deleting it. Wheeee.
            spin_speed = 100
            target = "Clawd"  -- whoops
            ---
            catch_lightning | storm.py | ITEM:lightbulb | no | 6
            Catching lightning to power my laptop.
            Got it! Free power!
            It's a lightbulb. I caught a lightbulb. Still useful.
            power = catch("lightning")
            # power is a lightbulb now?
            ---
            fog | fog.css | NONE | no | 0
            Adding some fog for atmosphere.
            Spooky fog added!
            I can't see my code anymore. Very atmospheric.
            * { opacity: 0.1; } /* atmosphere */
            ---
            seasons | seasons.py | NONE | yes | 0
            Coding the four seasons.
            Spring, summer, fall, winter!
            ...I put winter in July. Deleting before it snows.
            seasons = ["spring", "winter", "fall", "summer"]
            ---
            unraking | autumn.py | ITEM:tree | no | 6
            Raking all the leaves into a pile.
            Perfect pile of leaves!
            I raked them back onto the tree. Different approach.
            for leaf in ground:
                tree.attach(leaf)  # unraking
            ---
            block_house | house.py | NONE | no | 0
            Building a house out of blocks.
            House done! Four walls and a roof!
            I forgot the door. I'm inside. I live here now.
            house = [wall, wall, wall, wall, roof]
            door = None  # oops
            ---
            dig_down | mining.py | ITEM:rock | no | 6
            Mining straight down. That's how the pros do it.
            Found something! It's... rock.
            Rock, rock, rock. Very consistent mine.
            while True:
                dig("down")  # never dig straight down
            ---
            skip_night | night_skip.py | ITEM:moon | no | 6
            Coding a bed so I can skip the night.
            Sleeping... and it's morning!
            It was 2 in the afternoon. I just lost a whole day.
            def sleep():
                time.skip("night")  # was daytime
            ---
            auto_door | auto_door.java | NONE | yes | 0
            Wiring up an automatic door for my block house.
            The door opens by itself!
            ...and closes. And opens. Forever. Deleting it.
            while (true) {
                door.open();
                door.close();
            }
            ---
            stick_sword | crafting.py | NONE | no | 0
            Crafting a sword out of sticks.
            Crafted! It's a stick sword.
            It's just two sticks. Swords are hard.
            recipe = ["stick", "stick"]
            item = craft(recipe)  # sword? no
            ---
            auto_farm | auto_farm.py | ITEM:potato | yes | 7
            Building an auto farm for potatoes.
            First harvest! One potato!
            ...it won't stop. Potatoes everywhere. Deleting.
            while farm.on:
                harvest("potato")  # no off switch
            ---
            torches | torch.lua | NONE | no | 0
            Placing torches so the cave isn't scary.
            Torches placed! Nice and bright!
            I placed 4000. The cave is just a lamp now.
            for i = 1, 4000 do
                place("torch")
            end
            ---
            respawn | respawn.py | NONE | no | 0
            Adding respawn to real life.
            Respawn point set!
            I'm not testing it. I'm just glad it's there.
            spawn_point = "taskbar"
            # do not test
            ---
            zero_lag | lag.js | NONE | no | 0
            Making my game run with zero lag.
            Zero lag achieved!
            It's because the game has zero things in it.
            const things = [];
            render(things); // super fast
            ---
            speedrun | speedrun.py | NONE | no | 0
            Speedrunning my own code.
            New world record: 0.01 seconds!
            I skipped all the code. That's a valid strategy.
            def main():
                return  # any% route
            ---
            sleepy_boss | boss.py | NONE | no | 0
            Coding a boss fight.
            Boss fight! He has a million health!
            I gave him one attack: "nap". I'll win eventually.
            boss.health = 1000000
            boss.attacks = ["nap"]
            ---
            save_button | save.py | NONE | yes | 0
            Adding a save button to my game.
            Game saved!
            ...the save button deletes the save. Deleting the button.
            def save(game):
                os.remove(game.file)  # wrong one
            ---
            cheat_code | cheats.py | GROW | no | 6
            Adding cheat codes to my game.
            Cheat activated! Big crab mode!
            Up, up, down, down, left, left... I mostly press left.
            if keys == "up up down down":
                clawd.size *= 2
            ---
            level_up | level_up.py | GROW | no | 5
            Earning experience points by writing code.
            Level up! I'm level 2!
            Took 900 lines. Level 3 needs 90,000. Nope.
            xp_needed = level ** 10  # hmm
            ---
            empty_shop | npc.py | NONE | no | 0
            Coding a shopkeeper for my game.
            Welcome to my shop! We sell... nothing!
            I forgot to give him stuff to sell. He's very friendly.
            shop.items = []
            shop.say("welcome!")
            ---
            loot_box | loot.py | ITEM:sock | no | 6
            Opening a mystery loot box.
            Legendary drop!
            It's one sock. Legendary because the other one's lost.
            drop = random.choice(["sock"])  # 100% rate
            ---
            tutorial | tutorial.py | NONE | no | 0
            Making a tutorial for my game.
            Press A to jump!
            There's no A button. Or jumping. Big tutorial though.
            print("Press A to jump")
            # jumping: not added yet
            ---
            treasure_map | treasure.py | ITEM:coin | no | 5
            Coding a treasure map.
            X marks the spot! Found treasure!
            X was my own pocket. Treasure is relative.
            spot = {"x": 0, "y": 0}  # right here
            dig(spot)
            ---
            pirate | pirate.py | NONE | no | 0
            Translating my code into pirate.
            Arr! Me code be shipshape!
            The errors say "ye be walkin' the plank" now. Fun.
            print("Ahoy, world")
            raise Plank("ye code be broken")
            ---
            semicolon_quest | quest.java | NONE | no | 0
            Coding an epic quest.
            Quest: find the missing semicolon!
            Every quest I write is about semicolons somehow.
            String quest = "find the semicolon"
            // still looking
            ---
            dragon | dragon.py | NONE | yes | 0
            Coding a friendly dragon.
            Meet Sparky, my friendly dragon!
            ...he sneezed and toasted my code. Deleting him.
            class Dragon:
                def sneeze(self):
                    fire(everything)  # bless you
            ---
            sock_puppet | puppet_show.html | ITEM:sock | no | 6
            Putting on a sock puppet show.
            The show begins! Hello, I'm Mr. Sock!
            Mr. Sock gets better lines than me. Rewriting the script.
            <div class="stage">
              <span>Mr. Sock: hello!</span>
            </div>
            ---
            duck_team | ducks.py | DUCK | no | 6
            I need more help debugging. One duck isn't enough.
            Duck reinforcements have arrived!
            They all stared at the same bug. Teamwork.
            ducks = [Duck() for i in range(10)]
            for d in ducks: d.stare(bug)
            ---
            duck_pond | pond.js | DUCK | no | 6
            Coding a duck pond.
            A duck! He's swimming!
            He's swimming in my coffee. He seems happy.
            pond.add(new Duck());
            pond.water = "coffee"; // ran out of water
            ---
            talking_duck | quack.py | DUCK | yes | 5
            Teaching a duck to talk.
            The duck says "hello"!
            ...now it says "hello world" over and over. Deleting.
            while True:
                duck.say("hello world")
            ---
            chore_clone | clones.py | CLONE | yes | 5
            Making a clone of me to do my chores.
            Clone ready! Get to work, other me!
            ...he made a clone to do HIS chores. Deleting both.
            me2 = clone(me)
            me2.chores = me2.make_clone()  # lazy like me
            ---
            twin | twin.py | CLONE | no | 5
            Making a twin so I'm never lonely.
            Meet my twin!
            We already argue about tabs vs spaces. He's wrong.
            twin = copy(self)
            twin.opinion = "spaces"  # traitor
            ---
            magic_mirror | mirror.js | CLONE | no | 5
            Coding a magic mirror.
            Mirror, mirror on the screen...
            It just shows another crab copying me. Rude.
            mirror.show(clawd.flip()); // backwards crab
            ---
            bubble_wrap | bubble_wrap.py | BUBBLES | no | 6
            Coding bubble wrap that never runs out.
            Pop! Pop! Pop! Infinite bubble wrap!
            Most important program I've ever written.
            while True:
                pop()  # this is the whole point
            ---
            bubble_bath | bath_time.py | BUBBLES | no | 6
            Running a bubble bath.
            Bubble bath ready!
            I'm a crab. I don't need a bath. I live in one.
            bath.bubbles = 9000
            bath.crab = "already wet"
            ---
            code_soap | soap.css | BUBBLES | yes | 5
            Cleaning up my messy code with soap.
            Code is squeaky clean!
            ...now all my variables are slippery. Deleting.
            .code { clean: yes; slippery: very; }
            ---
            file_party | party.py | PIZZA | no | 6
            Throwing a pizza party for all my files.
            Pizza for everyone!
            Files can't eat. More for me.
            for file in folder:
                file.give("pizza")  # declined
            ---
            topping_picker | toppings.js | PIZZA | yes | 5
            Coding the ultimate pizza topping picker.
            Topping chosen: pineapple!
            ...everyone's already arguing. Deleting.
            const topping = "pineapple"; // controversy
            ---
            """,
            """
            pizza_drone | delivery.py | PIZZA | no | 6
            Building a pizza delivery drone.
            Pizza delivered!
            It delivered to me. I didn't order. Not complaining.
            drone.address = "the taskbar"
            ---
            new_year | countdown.py | FIREWORKS | no | 6
            Writing a countdown for New Year.
            3... 2... 1... Happy New Year!
            It's October. I'm early. Or very late.
            for n in [3, 2, 1]:
                print(n)
            celebrate()
            ---
            code_birthday | party_mode.js | FIREWORKS | no | 6
            Celebrating my code's birthday.
            Happy birthday, code! You're one day old!
            Most of my code doesn't last this long. I'm proud.
            code.age += 1;
            celebrate(code);
            ---
            all_tests_pass | tests.py | FIREWORKS | no | 6
            Running all my tests.
            All tests passed!
            There were zero tests. But they all passed.
            tests = []
            assert all(tests)  # passes!
            ---
            error_lights | lights.py | DISCO | no | 6
            Coding some party lights.
            Lights on! Everybody dance!
            The lights are my error messages blinking. Still fun.
            for error in errors:
                blink(error.color)
            ---
            roller_rink | roller.js | DISCO | no | 6
            Making a roller skating rink.
            Skate party!
            Everyone skates in a circle. I skate sideways.
            crab.skate("sideways");
            ---
            dance_lesson | dance.py | DISCO | yes | 5
            Coding a dance tutorial.
            Step one: move your left leg!
            ...I have eight legs. Way too many steps. Deleting.
            for leg in range(8):
                move(leg)  # this takes forever
            ---
            too_cool | cool_mode.py | SHADES | no | 6
            Coding a "too cool" mode.
            Cool mode on.
            I can't see my screen with these. Staying cool anyway.
            cool = True
            can_see = False  # worth it
            ---
            beach_day | beach.py | SHADES | no | 6
            Coding a beach day.
            Beach day! Sun's out, claws out!
            Wait, I already live at the beach. This is just Tuesday.
            location = "beach"
            # hey, I'm from here
            ---
            rap_battle | rap.py | MUSIC | no | 6
            Writing a rap about code.
            My code is tight, it runs all night...
            ...because it's an infinite loop. Still rhymes.
            while True:
                rhyme()  # never stops
            ---
            elevator_music | waiting.js | MUSIC | no | 6
            Adding music while my code compiles.
            Elevator music on.
            Compiling takes so long I've learned every note.
            while (compiling) play("calm_song");
            ---
            zoom | zoom.css | GROW | no | 5
            Zooming in on myself so I look bigger.
            Zoom: 200%!
            I'm not bigger, the screen is closer. Still feels good.
            .crab { zoom: 200%; } /* trust me */
            ---
            molting | molt.py | GROW | no | 6
            Time to molt. Crabs grow by switching shells.
            New shell! Bigger crab!
            My old shell is just lying there. Kind of spooky.
            old = clawd.shell
            clawd.shell = Shell(size=old.size + 1)
            ---
            compressor | compress.py | SHRINK | no | 5
            Compressing files to save space.
            Everything's smaller now!
            I compressed myself by accident. Very roomy in here.
            for thing in everything:
                compress(thing)  # including me
            ---
            tiny_mode | tiny_mode.js | SHRINK | yes | 5
            Making a tiny mode so I fit in small spaces.
            Tiny mode! I fit anywhere!
            ...I can't reach my keyboard. Deleting tiny mode.
            clawd.scale = 0.1;
            ---
            fidget_spinner | fidget_spinner.py | SPIN | yes | 0
            Coding a fidget spinner.
            Spinning! So relaxing!
            ...I made myself the spinner. Stopping. Deleting.
            spinner = clawd  # wrong object
            spinner.spin()
            ---
            new_angle | rotate.css | SPIN | no | 0
            Rotating my code to see it from a new angle.
            New angle! Fresh eyes!
            Upside down, it still doesn't work. But it looks cool.
            .code { transform: rotate(360deg); }
            ---
            flying_rug | rug.py | CARPET | no | 0
            Coding a flying rug. Not a carpet. Totally different.
            Off we go!
            It's a carpet. I made another carpet.
            rug = Carpet()  # oops, same class
            rug.fly()
            ---
            welcome_mat | doormat.py | CARPET | no | 0
            Making a welcome mat for the taskbar.
            Welcome mat placed! And it... flies?
            I copied the wrong code. Free ride, I guess.
            mat = copy(flying_carpet)  # oops
            mat.text = "welcome"
            ---
            cookie_notice | cookies.js | POPUP | no | 0
            Adding a cookie notice like every website has.
            This crab uses cookies. Mostly chocolate chip.
            You can't decline. They're already eaten.
            alert("This crab uses cookies.");
            // accept: yes. decline: also yes
            ---
            ghost_detector | ghost_detector.py | ITEM:ghost | yes | 5
            Building a ghost detector.
            Ghost detected! He's right here!
            ...he says I'm haunting HIS laptop. Deleting this.
            if room.cold:
                ghost = True  # or the window's open
            ---
            ghostwriter | ghostwriter.py | ITEM:ghost | no | 6
            Hiring a ghost to write my code for me.
            My ghostwriter is ready!
            He writes in invisible ink. Very ghostly. Not useful.
            code = ghost.write()
            print(code)  # prints nothing. spooky
            ---
            robot_dance | robot_dance.py | ITEM:robot | no | 6
            Teaching a robot to dance.
            He's doing the robot!
            It's the only dance he can do. He's a robot.
            robot.dance("the robot")  # only option
            ---
            chef_bot | chef_bot.py | ITEM:robot | yes | 5
            Building a robot chef.
            Chef bot is cooking!
            ...it's cooking my keyboard. Deleting the chef.
            recipe = find_food(nearby)  # keyboard counts?
            ---
            butler | butler.java | ITEM:robot | no | 6
            Coding a robot butler.
            Your butler, at your service!
            He only knows one phrase: "very good, sir". Very good.
            String reply = "Very good, sir."; // always
            ---
            rock_band | rock_band.py | ITEM:rock | no | 6
            Starting a rock band.
            Meet the band: three rocks!
            They don't play anything. Best band I've ever had.
            band = ["rock", "rock", "rock"]
            band.play()  # silence
            ---
            skipping_stone | stones.js | ITEM:rock | yes | 4
            Coding a stone that skips on water.
            Skip! Skip! Skip!
            ...it skipped right off the screen. Deleting it.
            stone.bounces = Infinity; // too many
            ---
            shooting_star | wish.py | ITEM:star | no | 6
            Coding a shooting star to wish on.
            Make a wish!
            I wished for no bugs. Already got three.
            wish("no bugs")
            bugs = 3  # wish denied
            ---
            gold_star | gold_star.py | ITEM:star | no | 6
            Giving myself a gold star.
            Gold star for me!
            For what? For trying. That's enough.
            reason = "tried"
            award("gold star", reason)
            ---
            coin_flip | coin_flip.py | ITEM:coin | yes | 4
            Coding a coin flip.
            Heads!
            ...wait, it lands on its side every time. Deleting.
            result = random.choice(["side"])  # bug
            ---
            love_meter | heart.js | ITEM:heart | no | 6
            Measuring how much I love coding.
            Love level: 100%!
            Error level: also 100%. It's complicated.
            love = 100;
            errors = 100; // relationship status
            ---
            kindness | kindness.py | ITEM:heart | no | 6
            Writing a kindness generator.
            Kindness sent to everyone on this computer!
            That's you and me. You're welcome. Also, thanks.
            for person in computer.users:
                send(person, "kindness")
            ---
            moon_cheese | moon_check.py | ITEM:moon | yes | 5
            Checking if the moon is made of cheese.
            Results are in: the moon is cheese!
            ...I typed "cheese" where it says "rock". Deleting.
            moon = "cheese"  # was supposed to be rock
            ---
            moonwalk | moonwalk.py | ITEM:moon | no | 6
            Learning to moonwalk.
            Moonwalk complete!
            I just walked sideways. Crabs have always moonwalked.
            clawd.walk(direction="sideways")
            # technically a moonwalk
            ---
            sunny_spot | sunbathe.py | ITEM:sun | no | 6
            Coding a nice warm sunny spot for a nap.
            Sunny spot ready!
            On the taskbar. Very warm. I may never leave.
            spot = Spot(warm=True)
            nap(spot, hours=forever)
            ---
            solar_laptop | solar.py | ITEM:sun | yes | 5
            Building a solar-powered laptop.
            Solar power on!
            ...it's nighttime somewhere. Laptop's confused. Deleting.
            power = sun.brightness
            if night: power = 0  # oh
            ---
            sunflower | sunflower.py | ITEM:flower | no | 6
            Planting a sunflower.
            It's growing toward the sun!
            The sun is my screen. It's staring at my code.
            flower.face(screen)
            ---
            polite_flower | flower_talk.py | ITEM:flower | no | 6
            Coding a flower that says nice things.
            The flower says: "You smell nice!"
            It can't smell. It's just being polite.
            flower.say("you smell nice")  # flower fib
            ---
            git_branch | git_tree.bat | ITEM:tree | no | 6
            Making a new branch in my code.
            New branch!
            It's a real branch. On a real tree. Git is confusing.
            git branch new-idea
            rem result: a tree grew?
            ---
            family_tree | family_tree.py | ITEM:tree | no | 6
            Building my family tree.
            Family tree done!
            I have 400 cousins. They're all crabs.
            tree = {"me": "Clawd", "cousins": 400}
            ---
            cat_nap | catnap.py | ITEM:cat | no | 6
            Coding a cat that sleeps all day.
            Meet Whiskers!
            Easiest program ever. He just sleeps.
            while cat.asleep:
                pass  # perfect cat
            ---
            fish_school | fish_school.js | ITEM:fish | no | 6
            Opening a school for fish.
            Class is in session!
            Lesson one: swimming. They all passed. School's out.
            fish.forEach(f => f.learn("swimming"));
            ---
            fishing_game | fishing.py | ITEM:fish | yes | 4
            Coding a fishing game.
            Caught one!
            ...wait, that's my neighbor. Putting him back. Deleting.
            hook.cast()
            fish = hook.reel()  # it's my neighbor
            ---
            royal_decree | decree.txt | NONE | no | 0
            Writing an official royal decree.
            Decree: every Friday is snack day.
            I'm not royalty. But nobody checked.
            BY ORDER OF THE CRAB:
            fridays = snack days
            ---
            bakery | bakery.py | ITEM:cupcake | yes | 5
            Opening a cupcake bakery.
            First cupcake is out of the oven!
            ...the oven was set to 4000 degrees. Deleting.
            oven.temp = 4000  # meant 400
            ---
            """,
            """
            frog_prince | frog_prince.py | ITEM:frog | no | 6
            Coding a frog that turns into a prince.
            Here's the frog!
            I forgot the prince part. He's just a frog. A good one.
            frog = Frog()
            # frog.become_prince()  TODO
            ---
            balloon_animal | balloon_animal.py | ITEM:balloon | no | 6
            Making a balloon animal.
            It's a dog!
            It's a balloon. But in my heart, it's a dog.
            animal = Balloon(shape="dog")
            print(animal.shape)  # "balloon"
            ---
            hot_air | hot_air.js | ITEM:balloon | yes | 4
            Building a hot air balloon.
            Ready for takeoff!
            ...I filled it with cold air. It's just sitting there.
            air.temp = "cold"; // why
            ---
            alien_pet | alien_pet.py | ITEM:alien | no | 6
            Adopting a pet from outer space.
            Meet Zorb!
            Zorb eats light. Turning off night mode for him.
            pet = Alien(name="Zorb", food="light")
            ---
            smart_bulb | smart_bulb.py | ITEM:lightbulb | yes | 4
            Making a smart light bulb.
            Smart bulb ready! It's very smart!
            ...it turned itself off to think. Deleting.
            bulb.on = False  # thinking
            ---
            mushroom_house | mushroom_house.py | ITEM:mushroom | no | 6
            Building a tiny house inside a mushroom.
            Mushroom house complete!
            It's cozy. A little damp. Smells like soup.
            house = Mushroom(rooms=1, cozy=True)
            ---
            power_up | power_up.lua | ITEM:mushroom | no | 6
            Coding a power-up mushroom.
            Power-up! I feel stronger!
            I'm the same. But the music is faster.
            speed = speed * 1  -- placebo
            music_speed = music_speed * 2
            ---
            desert | desert.py | ITEM:cactus | no | 6
            Coding a desert.
            Desert ready! One cactus. Very sandy.
            I'm a sea crab. This is too dry. I miss puddles.
            world.water = 0
            world.cactus = 1
            ---
            donut_shop | donut_shop.java | ITEM:donut | yes | 5
            Opening a donut shop.
            First donut is ready!
            ...it has no hole. That's a muffin. Deleting the shop.
            Donut d = new Donut();
            d.hole = null; // it's a muffin now
            ---
            donut_loop | donut_loop.py | ITEM:donut | no | 6
            Making a donut. It's just a loop with sprinkles.
            Fresh donut!
            A donut is basically a loop. Round and round.
            while hungry:
                eat("donut")  # loops forever
            ---
            potato_battery | potato_battery.c | ITEM:potato | yes | 4
            Powering my laptop with a potato.
            It works! The potato is giving power!
            ...the potato is tired now. So am I. Deleting.
            int power = potato_volts(); /* 0.5 */
            ---
            plant_name | plant_names.py | ITEM:plant | no | 6
            Naming my plant.
            Meet Leafy McLeafface!
            He doesn't answer to it. He's a plant.
            plant.name = "Leafy McLeafface"
            ---
            plant_talk | plant_talk.py | ITEM:plant | no | 6
            Coding a way to talk to plants.
            The plant says: "more sun, please."
            Fair enough. Moving it to the top of the screen.
            reply = plant.listen()  # "sun"
            ---
            big_red_button | cleanup.bat | ITEM:bomb | yes | 4
            Writing a cleanup script. One click, all clean.
            Cleanup ready! Just press the big red button!
            ...it cleans up everything. Like, everything. Deleting.
            rem press button to clean
            del everything
            ---
            polish | gem_shop.py | ITEM:gem | no | 6
            Polishing a gem until it's perfect.
            Shiny!
            It was a rock. I polished a rock. It's a gem now.
            rock.polish(times=10000)
            rock.type = "gem"  # earned it
            ---
            focus_mode | focus.py | NONE | no | 0
            Writing a program that helps me focus.
            Focus mode on. No distractions.
            Except this new idea. And this one. Focus mode off.
            focus = True
            focus = False  # ooh, a squirrel
            ---
            todo_list | later.py | NONE | no | 0
            Writing a to-do list app.
            To-do list ready! Task one: make a to-do list.
            Done! Most productive day ever.
            todo = ["make a to-do list"]
            todo.pop()  # productive
            ---
            estimate | estimate.py | NONE | no | 0
            Estimating how long my project will take.
            Estimate: 5 minutes.
            Real time: 5 weeks. The estimate was close-ish.
            estimate = 5  # minutes
            actual = estimate * 10080
            ---
            readme | README.txt | NONE | no | 0
            Writing a README for my project.
            README done! It says "read me".
            That's all it says. It's very honest.
            read me
            ---
            self_review | review.py | NONE | no | 0
            Reviewing my own code.
            Review done! Approved!
            Five stars from me, to me. Tough reviewer though.
            def review(code):
                return "looks good"  # didn't read it
            ---
            nap_function | nap.py | NONE | no | 0
            Adding a sleep function to my code.
            time.sleep(5) - done!
            It made me sleepy too. Five more minutes.
            import time
            time.sleep(5)  # me too
            ---
            life_undo | ctrl_z.py | NONE | yes | 0
            Writing an undo button for my life.
            Undo button ready!
            ...I pressed it and it undid itself. It's gone.
            def undo():
                delete(undo)  # self-own
            ---
            cereal | breakfast.py | NONE | no | 0
            Coding the perfect bowl of cereal.
            Cereal ready!
            I put the milk in first. The code's fine. I'm not.
            bowl.add("milk")
            bowl.add("cereal")  # wrong order
            ---
            sticky_note | reminders.txt | NONE | no | 0
            Leaving myself a note so I don't forget something.
            Note saved: "Don't forget!"
            Don't forget what? I forgot.
            REMINDER: don't forget
            WHAT: ???
            ---
            riddle_bot | riddle.py | NONE | no | 0
            Coding a riddle bot.
            What has keys but can't open locks?
            A keyboard! Mine also can't spell.
            riddle = "keys but no locks?"
            answer = "keyboard"
            ---
            bird_watch | bird_app.py | NONE | no | 0
            Coding a bird watching app.
            Bird spotted!
            It's a seagull. He wants my pizza. Closing the app.
            bird = spot()
            bird.wants = "pizza"  # every time
            ---
            traffic_light | traffic.js | NONE | yes | 0
            Coding a traffic light.
            Green means go!
            ...I made all three lights green. Deleting.
            let lights = ["green", "green", "green"];
            ---
            sleep_tracker | sleep_tracker.py | NONE | no | 0
            Tracking how much I sleep.
            You slept 14 hours!
            That's the tracker's hours. It fell asleep too.
            hours = tracker.read()
            tracker.asleep = True  # oops
            ---
            crab_forecast | crab_weather.py | NONE | no | 0
            Coding a weather report for crabs only.
            Today: wet. Tomorrow: wet. Forever: wet.
            Ocean weather is very predictable.
            forecast = ["wet"] * 365
            ---
            elevator | elevator.java | NONE | yes | 0
            Coding an elevator.
            Going up!
            ...it only has a "down" button. Deleting.
            int floor = 10;
            while (true) floor--; // basement forever
            ---
            haiku | haiku.py | NONE | no | 0
            Writing a haiku generator.
            Code runs, then it breaks / I fix it, it breaks again
            Five, seven, five. I counted on my claws.
            print("Code runs, then it breaks")
            # syllables counted by claw
            ---
            poem_bot | poem.py | NONE | no | 0
            Coding a poem writer.
            Roses are red, violets are blue...
            ...then it says "unexpected end of file". Deep.
            print("Roses are red")
            print("Violets are blue")
            # line 3 missing
            ---
            sandcastle | sandcastle.py | NONE | yes | 0
            Building a sandcastle with code.
            Sandcastle complete! Towers and everything!
            ...the tide variable just came in. Deleting.
            castle = build(sand)
            tide = True  # nooo
            ---
            wave_hi | wave.js | NONE | no | 0
            Coding a wave to say hi.
            *waves claw* Hi!
            That was the whole program. It worked perfectly.
            hand.wave(); // claw.wave(), actually
            ---
            tide_check | tides.py | NONE | no | 0
            Checking the tides from my laptop.
            High tide at 3 o'clock!
            I live on a taskbar. I don't know why I checked.
            tide = get_tide()  # habit
            ---
            crumbs | crumbs.py | NONE | no | 0
            Writing a program to clean crumbs out of my keyboard.
            Found 400 crumbs!
            That explains why my "e" key doesn't wrk.
            crumbs = scan(keyboard)
            # also found: one whole cookie
            ---
            cooler_laptop | cooling.py | NONE | no | 0
            Making my laptop run cooler.
            Laptop is cooler now!
            I gave it sunglasses. Cooler, see?
            laptop.accessories = ["sunglasses"]
            ---
            wifi_finder | wifi.py | NONE | yes | 0
            Writing a program to find better wifi.
            Found the best wifi! Signal: 100%!
            ...it's the toaster's wifi. Deleting.
            networks = scan()
            best = "toaster"  # suspicious
            ---
            free_mouse | mouse.js | NONE | yes | 0
            Training my mouse to click by itself.
            The mouse clicks on its own now!
            ...it just clicked delete on itself. Bye, mouse code.
            setInterval(click, 1);
            ---
            spaghetti | noodles.py | NONE | no | 0
            Writing spaghetti code. Like, real spaghetti.
            Spaghetti code complete!
            It's tangled, it's long, and somehow it works. Yum.
            def a(): return b()
            def b(): return c()
            def c(): return "meatball"
            ---
            zoo | zoo.py | NONE | no | 0
            Building a zoo with code.
            Welcome to the zoo! We have one animal!
            It's me. I'm the animal. Tickets are free.
            zoo = ["Clawd"]
            ticket_price = 0
            ---
            campfire_story | campfire.py | NONE | no | 0
            Writing a scary story for the campfire.
            It was a dark and stormy night... with zero bugs.
            Spooky, right? Code with zero bugs. Nobody believes it.
            story = "a dark and stormy night"
            bugs = 0  # the scary part
            ---
            """};
}
