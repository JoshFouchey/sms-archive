-- Demo data for semantic search demonstrations.
-- Creates 4 new contacts with rich, diverse conversations.
-- Run: docker compose exec -T db psql -U sms_user -d sms_archive < scripts/insert-demo-search-data.sql
-- After inserting, trigger embedding from the UI (Settings > Re-embed All Messages).

DO $$
DECLARE
    v_user_id UUID := '7e2abc57-b877-4210-ae93-b9686f8a5276';
    v_bob_id BIGINT;
    v_mom_id BIGINT;
    v_mike_id BIGINT;
    v_sarah_id BIGINT;
    v_conv_bob BIGINT;
    v_conv_mom BIGINT;
    v_conv_mike BIGINT;
    v_conv_sarah BIGINT;
    v_base TIMESTAMP := '2025-07-01 09:00:00';
BEGIN

-- ============================================================
-- CONTACTS
-- ============================================================
INSERT INTO contacts (user_id, number, normalized_number, name, created_at, updated_at)
VALUES (v_user_id, '+1 (303) 555-0101', '+13035550101', 'Bob Jones', now(), now())
ON CONFLICT (user_id, normalized_number) DO UPDATE SET name = 'Bob Jones'
RETURNING id INTO v_bob_id;

INSERT INTO contacts (user_id, number, normalized_number, name, created_at, updated_at)
VALUES (v_user_id, '+1 (303) 555-0102', '+13035550102', 'Mom', now(), now())
ON CONFLICT (user_id, normalized_number) DO UPDATE SET name = 'Mom'
RETURNING id INTO v_mom_id;

INSERT INTO contacts (user_id, number, normalized_number, name, created_at, updated_at)
VALUES (v_user_id, '+1 (303) 555-0103', '+13035550103', 'Mike Chen', now(), now())
ON CONFLICT (user_id, normalized_number) DO UPDATE SET name = 'Mike Chen'
RETURNING id INTO v_mike_id;

INSERT INTO contacts (user_id, number, normalized_number, name, created_at, updated_at)
VALUES (v_user_id, '+1 (303) 555-0104', '+13035550104', 'Sarah Williams', now(), now())
ON CONFLICT (user_id, normalized_number) DO UPDATE SET name = 'Sarah Williams'
RETURNING id INTO v_sarah_id;

-- ============================================================
-- CONVERSATION 1: Bob Jones — New house, job, family life
-- ============================================================
INSERT INTO conversations (user_id, name, last_message_at, created_at, updated_at)
VALUES (v_user_id, 'Bob Jones', v_base + interval '7 days', now(), now())
RETURNING id INTO v_conv_bob;

INSERT INTO conversation_contacts (conversation_id, contact_id) VALUES (v_conv_bob, v_bob_id);

INSERT INTO messages (user_id, conversation_id, sender_contact_id, protocol, direction, timestamp, body, created_at, updated_at) VALUES
-- Day 1: Big news about moving
(v_user_id, v_conv_bob, v_bob_id, 'SMS', 'INBOUND', v_base,
 'Dude we just closed on the house! We officially live in Denver now!', now(), now()),
(v_user_id, v_conv_bob, NULL, 'SMS', 'OUTBOUND', v_base + interval '3 min',
 'No way! Congrats man! What neighborhood?', now(), now()),
(v_user_id, v_conv_bob, v_bob_id, 'SMS', 'INBOUND', v_base + interval '5 min',
 'We''re in Wash Park. 3 bed 2 bath with a nice backyard for the kids. Emma is already obsessed with the swing set', now(), now()),
(v_user_id, v_conv_bob, NULL, 'SMS', 'OUTBOUND', v_base + interval '7 min',
 'That sounds amazing. How old is Emma now? I feel like it was just yesterday she was born', now(), now()),
(v_user_id, v_conv_bob, v_bob_id, 'SMS', 'INBOUND', v_base + interval '9 min',
 'She just turned 4 in March! And Jake is 7 now. Time flies man', now(), now()),
(v_user_id, v_conv_bob, NULL, 'SMS', 'OUTBOUND', v_base + interval '11 min',
 'Crazy. How''s the new job going?', now(), now()),
(v_user_id, v_conv_bob, v_bob_id, 'SMS', 'INBOUND', v_base + interval '14 min',
 'Love it honestly. I''m doing DevOps at Lockheed Martin now. Way better work-life balance than the startup', now(), now()),
(v_user_id, v_conv_bob, NULL, 'SMS', 'OUTBOUND', v_base + interval '16 min',
 'Lockheed! That''s a big gig. Defense stuff?', now(), now()),
(v_user_id, v_conv_bob, v_bob_id, 'SMS', 'INBOUND', v_base + interval '18 min',
 'Yeah satellite systems. Can''t say much obviously but it''s really interesting work. Plus the 401k match is insane', now(), now()),

-- Day 3: Weekend plans and hobbies
(v_user_id, v_conv_bob, v_bob_id, 'SMS', 'INBOUND', v_base + interval '2 days',
 'Hey you should come visit sometime. We''ve got a spare room. Plus the hiking out here is unreal', now(), now()),
(v_user_id, v_conv_bob, NULL, 'SMS', 'OUTBOUND', v_base + interval '2 days 2 min',
 'I''d love that. I''ve been wanting to do some 14ers. You still mountain biking?', now(), now()),
(v_user_id, v_conv_bob, v_bob_id, 'SMS', 'INBOUND', v_base + interval '2 days 4 min',
 'Every weekend! I got a new Santa Cruz Hightower. Lisa thinks I''m crazy but the trails here are incredible', now(), now()),
(v_user_id, v_conv_bob, NULL, 'SMS', 'OUTBOUND', v_base + interval '2 days 6 min',
 'How is Lisa doing? Still at the hospital?', now(), now()),
(v_user_id, v_conv_bob, v_bob_id, 'SMS', 'INBOUND', v_base + interval '2 days 8 min',
 'Yeah she transferred to UCHealth here in Denver. She''s an ER nurse now instead of ICU. Way less stressful apparently', now(), now()),

-- Day 5: Food and recommendations
(v_user_id, v_conv_bob, NULL, 'SMS', 'OUTBOUND', v_base + interval '4 days',
 'Random question but do you know any good BBQ spots in Denver? Planning a trip out there next month', now(), now()),
(v_user_id, v_conv_bob, v_bob_id, 'SMS', 'INBOUND', v_base + interval '4 days 3 min',
 'Bro you HAVE to go to Owlbear BBQ. Best brisket I''ve ever had. Get the burnt ends platter. Trust me', now(), now()),
(v_user_id, v_conv_bob, NULL, 'SMS', 'OUTBOUND', v_base + interval '4 days 5 min',
 'Done. Also are you still allergic to shellfish? Want to make sure I pick the right restaurants', now(), now()),
(v_user_id, v_conv_bob, v_bob_id, 'SMS', 'INBOUND', v_base + interval '4 days 7 min',
 'Yeah still can''t do shellfish. But there''s a great sushi place called Sushi Den that has tons of non-shellfish options', now(), now()),
(v_user_id, v_conv_bob, NULL, 'SMS', 'OUTBOUND', v_base + interval '4 days 9 min',
 'Perfect. Oh and Lisa''s birthday is coming up right? August something?', now(), now()),
(v_user_id, v_conv_bob, v_bob_id, 'SMS', 'INBOUND', v_base + interval '4 days 11 min',
 'August 12th! Good memory. We''re throwing a party at the house. You should totally come for that', now(), now()),

-- Day 7: Cars and pets
(v_user_id, v_conv_bob, v_bob_id, 'SMS', 'INBOUND', v_base + interval '7 days',
 'We finally got a dog btw! Golden retriever puppy named Cooper. The kids are losing their minds', now(), now()),
(v_user_id, v_conv_bob, NULL, 'SMS', 'OUTBOUND', v_base + interval '7 days 2 min',
 'Omg send pics! I love goldens. Did you end up getting that truck you were looking at?', now(), now()),
(v_user_id, v_conv_bob, v_bob_id, 'SMS', 'INBOUND', v_base + interval '7 days 5 min',
 'Yeah! Picked up a Ford F-150 Lightning last month. Electric truck is perfect for Colorado. Lisa drives the Subaru Outback', now(), now());

-- ============================================================
-- CONVERSATION 2: Mom — Family, recipes, health, events
-- ============================================================
INSERT INTO conversations (user_id, name, last_message_at, created_at, updated_at)
VALUES (v_user_id, 'Mom', v_base + interval '10 days', now(), now())
RETURNING id INTO v_conv_mom;

INSERT INTO conversation_contacts (conversation_id, contact_id) VALUES (v_conv_mom, v_mom_id);

INSERT INTO messages (user_id, conversation_id, sender_contact_id, protocol, direction, timestamp, body, created_at, updated_at) VALUES
-- Day 1: Family dinner planning
(v_user_id, v_conv_mom, v_mom_id, 'SMS', 'INBOUND', v_base + interval '1 day',
 'Hi sweetie! Are you coming to Grandma''s 80th birthday party? It''s on September 14th at Uncle Dave''s house', now(), now()),
(v_user_id, v_conv_mom, NULL, 'SMS', 'OUTBOUND', v_base + interval '1 day 5 min',
 'Of course! Wouldn''t miss it. Should I bring anything?', now(), now()),
(v_user_id, v_conv_mom, v_mom_id, 'SMS', 'INBOUND', v_base + interval '1 day 8 min',
 'Can you make your famous chocolate cake? Grandma Rose always asks about it', now(), now()),
(v_user_id, v_conv_mom, NULL, 'SMS', 'OUTBOUND', v_base + interval '1 day 10 min',
 'Sure! Can you send me the recipe again? I lost it', now(), now()),
(v_user_id, v_conv_mom, v_mom_id, 'SMS', 'INBOUND', v_base + interval '1 day 12 min',
 'It''s 2 cups flour, 1.5 cups sugar, 3/4 cup cocoa powder, 2 eggs, 1 cup buttermilk, half cup vegetable oil, 2 tsp vanilla, 1 tsp baking soda. Bake at 350 for 30 minutes', now(), now()),
(v_user_id, v_conv_mom, NULL, 'SMS', 'OUTBOUND', v_base + interval '1 day 14 min',
 'Got it saved this time. Is Aunt Karen coming? I heard she was sick', now(), now()),
(v_user_id, v_conv_mom, v_mom_id, 'SMS', 'INBOUND', v_base + interval '1 day 17 min',
 'She had knee surgery last month but she''s recovering well. She''ll be there with a walker probably', now(), now()),

-- Day 4: Health stuff
(v_user_id, v_conv_mom, v_mom_id, 'SMS', 'INBOUND', v_base + interval '4 days',
 'Just got back from the doctor. My blood pressure is a little high so they put me on lisinopril', now(), now()),
(v_user_id, v_conv_mom, NULL, 'SMS', 'OUTBOUND', v_base + interval '4 days 3 min',
 'Oh no are you ok? Is it serious?', now(), now()),
(v_user_id, v_conv_mom, v_mom_id, 'SMS', 'INBOUND', v_base + interval '4 days 5 min',
 'I''m fine honey don''t worry. Doctor said it''s very manageable. I just need to cut back on sodium and walk more', now(), now()),
(v_user_id, v_conv_mom, NULL, 'SMS', 'OUTBOUND', v_base + interval '4 days 7 min',
 'Ok good. You should try that Mediterranean diet. Everyone says it helps with blood pressure', now(), now()),
(v_user_id, v_conv_mom, v_mom_id, 'SMS', 'INBOUND', v_base + interval '4 days 9 min',
 'Your father would never survive without his steak haha. But I did sign up for a yoga class at the Y. Tuesday and Thursday mornings', now(), now()),

-- Day 7: Family updates
(v_user_id, v_conv_mom, v_mom_id, 'SMS', 'INBOUND', v_base + interval '7 days',
 'Your sister Emily got promoted to VP at her company! So proud of her', now(), now()),
(v_user_id, v_conv_mom, NULL, 'SMS', 'OUTBOUND', v_base + interval '7 days 2 min',
 'That''s awesome! Tell her congrats. Is she still at Salesforce?', now(), now()),
(v_user_id, v_conv_mom, v_mom_id, 'SMS', 'INBOUND', v_base + interval '7 days 4 min',
 'Yes still at Salesforce in San Francisco. She and David are thinking about buying a house in Noe Valley', now(), now()),
(v_user_id, v_conv_mom, NULL, 'SMS', 'OUTBOUND', v_base + interval '7 days 6 min',
 'Noe Valley is so expensive though. How are the kids?', now(), now()),
(v_user_id, v_conv_mom, v_mom_id, 'SMS', 'INBOUND', v_base + interval '7 days 8 min',
 'Little Sophia is starting kindergarten in the fall! And Oliver just learned to ride his bike. Your dad is teaching him', now(), now()),

-- Day 10: Pet news
(v_user_id, v_conv_mom, v_mom_id, 'SMS', 'INBOUND', v_base + interval '10 days',
 'Mittens had her vet appointment today. Doctor says she''s perfectly healthy for a 12 year old cat', now(), now()),
(v_user_id, v_conv_mom, NULL, 'SMS', 'OUTBOUND', v_base + interval '10 days 3 min',
 'Good to hear! Give Mittens some treats for me. Also Dad''s birthday is coming up. What should I get him?', now(), now()),
(v_user_id, v_conv_mom, v_mom_id, 'SMS', 'INBOUND', v_base + interval '10 days 5 min',
 'He really wants a new fishing rod. He''s been looking at the Shimano Stradic. His birthday is October 3rd don''t forget!', now(), now());

-- ============================================================
-- CONVERSATION 3: Mike Chen — Sports, travel, tech
-- ============================================================
INSERT INTO conversations (user_id, name, last_message_at, created_at, updated_at)
VALUES (v_user_id, 'Mike Chen', v_base + interval '8 days', now(), now())
RETURNING id INTO v_conv_mike;

INSERT INTO conversation_contacts (conversation_id, contact_id) VALUES (v_conv_mike, v_mike_id);

INSERT INTO messages (user_id, conversation_id, sender_contact_id, protocol, direction, timestamp, body, created_at, updated_at) VALUES
-- Day 1: Sports
(v_user_id, v_conv_mike, v_mike_id, 'SMS', 'INBOUND', v_base + interval '2 days',
 'Did you see the Lakers game last night? LeBron had 42 points it was insane', now(), now()),
(v_user_id, v_conv_mike, NULL, 'SMS', 'OUTBOUND', v_base + interval '2 days 2 min',
 'I missed it! Was at my volleyball league. We won our match though 3 sets to 1', now(), now()),
(v_user_id, v_conv_mike, v_mike_id, 'SMS', 'INBOUND', v_base + interval '2 days 4 min',
 'Nice! When is your league? I''ve been wanting to join a rec league for something. Maybe basketball or soccer', now(), now()),
(v_user_id, v_conv_mike, NULL, 'SMS', 'OUTBOUND', v_base + interval '2 days 6 min',
 'Wednesday nights at the community center. You should come watch sometime. Season ends in August', now(), now()),

-- Day 4: Travel planning
(v_user_id, v_conv_mike, v_mike_id, 'SMS', 'INBOUND', v_base + interval '4 days',
 'Bro I booked flights to Japan! Going to Tokyo and Kyoto in November. Two weeks', now(), now()),
(v_user_id, v_conv_mike, NULL, 'SMS', 'OUTBOUND', v_base + interval '4 days 3 min',
 'That''s been on my bucket list forever. Where are you staying?', now(), now()),
(v_user_id, v_conv_mike, v_mike_id, 'SMS', 'INBOUND', v_base + interval '4 days 5 min',
 'Airbnb in Shinjuku for Tokyo and a traditional ryokan in Kyoto. My girlfriend Amy planned the whole thing. She studied Japanese in college', now(), now()),
(v_user_id, v_conv_mike, NULL, 'SMS', 'OUTBOUND', v_base + interval '4 days 7 min',
 'Amy speaks Japanese? That''s so useful. You gotta try the ramen at Ichiran in Shibuya. Life changing', now(), now()),
(v_user_id, v_conv_mike, v_mike_id, 'SMS', 'INBOUND', v_base + interval '4 days 9 min',
 'Already on the list! Also want to check out the fish market and maybe climb Mt Fuji if the weather is good', now(), now()),

-- Day 6: Tech talk
(v_user_id, v_conv_mike, v_mike_id, 'SMS', 'INBOUND', v_base + interval '6 days',
 'Have you tried that new Claude AI thing? I used it to refactor some Python code at work and it was shockingly good', now(), now()),
(v_user_id, v_conv_mike, NULL, 'SMS', 'OUTBOUND', v_base + interval '6 days 3 min',
 'Yeah I''ve been using it for a side project. What are you working on at work these days?', now(), now()),
(v_user_id, v_conv_mike, v_mike_id, 'SMS', 'INBOUND', v_base + interval '6 days 5 min',
 'Still at Spotify. We''re building a new recommendation engine using transformer models. Really cool ML stuff', now(), now()),
(v_user_id, v_conv_mike, NULL, 'SMS', 'OUTBOUND', v_base + interval '6 days 7 min',
 'That sounds awesome. I thought you were doing backend stuff?', now(), now()),
(v_user_id, v_conv_mike, v_mike_id, 'SMS', 'INBOUND', v_base + interval '6 days 9 min',
 'I switched to the ML team 6 months ago. Got my masters in machine learning from Georgia Tech last year. Best decision ever', now(), now()),

-- Day 8: Movie recommendations
(v_user_id, v_conv_mike, NULL, 'SMS', 'OUTBOUND', v_base + interval '8 days',
 'Hey what was that movie you recommended last week? The sci fi one?', now(), now()),
(v_user_id, v_conv_mike, v_mike_id, 'SMS', 'INBOUND', v_base + interval '8 days 2 min',
 'Dune Part Two! Seriously the best movie I''ve seen all year. The visuals are insane. Also check out 3 Body Problem on Netflix', now(), now()),
(v_user_id, v_conv_mike, NULL, 'SMS', 'OUTBOUND', v_base + interval '8 days 4 min',
 'Cool will add both to my list. Oh btw Amy''s birthday is coming up right? Need gift ideas?', now(), now()),
(v_user_id, v_conv_mike, v_mike_id, 'SMS', 'INBOUND', v_base + interval '8 days 6 min',
 'July 22nd! I''m getting her a Kindle Paperwhite. She reads like 3 books a week it''s crazy', now(), now());

-- ============================================================
-- CONVERSATION 4: Sarah Williams — Wedding, career, food
-- ============================================================
INSERT INTO conversations (user_id, name, last_message_at, created_at, updated_at)
VALUES (v_user_id, 'Sarah Williams', v_base + interval '9 days', now(), now())
RETURNING id INTO v_conv_sarah;

INSERT INTO conversation_contacts (conversation_id, contact_id) VALUES (v_conv_sarah, v_sarah_id);

INSERT INTO messages (user_id, conversation_id, sender_contact_id, protocol, direction, timestamp, body, created_at, updated_at) VALUES
-- Day 1: Wedding planning
(v_user_id, v_conv_sarah, v_sarah_id, 'SMS', 'INBOUND', v_base + interval '1 day',
 'OMG we set the date!! October 18th! I''m getting married!!!', now(), now()),
(v_user_id, v_conv_sarah, NULL, 'SMS', 'OUTBOUND', v_base + interval '1 day 1 min',
 'AHHH CONGRATS!!! I''m so happy for you and Marcus! Where''s the venue?', now(), now()),
(v_user_id, v_conv_sarah, v_sarah_id, 'SMS', 'INBOUND', v_base + interval '1 day 3 min',
 'The Broadmoor in Colorado Springs! We toured it last weekend and both fell in love. It''s going to be about 150 guests', now(), now()),
(v_user_id, v_conv_sarah, NULL, 'SMS', 'OUTBOUND', v_base + interval '1 day 5 min',
 'The Broadmoor is gorgeous. I went there for a conference once. Is your sister doing maid of honor?', now(), now()),
(v_user_id, v_conv_sarah, v_sarah_id, 'SMS', 'INBOUND', v_base + interval '1 day 7 min',
 'Yes! Jessica is maid of honor and Marcus''s best friend Derek is best man. Oh and I have to tell you... I want you to do a reading at the ceremony!', now(), now()),
(v_user_id, v_conv_sarah, NULL, 'SMS', 'OUTBOUND', v_base + interval '1 day 9 min',
 'I would be honored!! Just tell me when and what to read. Have you picked your dress yet?', now(), now()),
(v_user_id, v_conv_sarah, v_sarah_id, 'SMS', 'INBOUND', v_base + interval '1 day 11 min',
 'Found it last Saturday at Kleinfeld''s! It''s a Vera Wang. I ugly cried in the fitting room. My mom was there too of course', now(), now()),

-- Day 4: Career stuff
(v_user_id, v_conv_sarah, v_sarah_id, 'SMS', 'INBOUND', v_base + interval '4 days',
 'So remember how I told you I applied for that teaching position? I GOT IT', now(), now()),
(v_user_id, v_conv_sarah, NULL, 'SMS', 'OUTBOUND', v_base + interval '4 days 2 min',
 'Wait the one at CU Boulder?? That''s your dream job! When do you start?', now(), now()),
(v_user_id, v_conv_sarah, v_sarah_id, 'SMS', 'INBOUND', v_base + interval '4 days 4 min',
 'Fall semester! I''ll be teaching Intro to Psychology and Developmental Psych. I''m so nervous but also so excited', now(), now()),
(v_user_id, v_conv_sarah, NULL, 'SMS', 'OUTBOUND', v_base + interval '4 days 6 min',
 'You''re going to be an amazing professor. You''ve wanted this since grad school', now(), now()),
(v_user_id, v_conv_sarah, v_sarah_id, 'SMS', 'INBOUND', v_base + interval '4 days 8 min',
 'I know right?! My PhD advisor Dr. Chen wrote me an incredible recommendation letter. I still can''t believe it', now(), now()),

-- Day 7: Food and restaurants
(v_user_id, v_conv_sarah, NULL, 'SMS', 'OUTBOUND', v_base + interval '7 days',
 'Where should Marcus and I go for our anniversary dinner? You always know the best spots', now(), now()),
(v_user_id, v_conv_sarah, v_sarah_id, 'SMS', 'INBOUND', v_base + interval '7 days 3 min',
 'Omg try Beckon in Denver. It''s a tasting menu restaurant. 8 courses and every single one is a masterpiece. Marcus would love it', now(), now()),
(v_user_id, v_conv_sarah, NULL, 'SMS', 'OUTBOUND', v_base + interval '7 days 5 min',
 'That sounds perfect. Is it super expensive?', now(), now()),
(v_user_id, v_conv_sarah, v_sarah_id, 'SMS', 'INBOUND', v_base + interval '7 days 7 min',
 'About $175 per person but worth every penny. Also if you want something more casual check out Hop Alley for Chinese food. Their dan dan noodles are addictive', now(), now()),

-- Day 9: Pets and weekend
(v_user_id, v_conv_sarah, v_sarah_id, 'SMS', 'INBOUND', v_base + interval '9 days',
 'Look at this face! Just adopted a rescue dog from the Dumb Friends League. Her name is Luna she''s a border collie mix', now(), now()),
(v_user_id, v_conv_sarah, NULL, 'SMS', 'OUTBOUND', v_base + interval '9 days 2 min',
 'She''s so cute!! How''s she getting along with your cat Whiskers?', now(), now()),
(v_user_id, v_conv_sarah, v_sarah_id, 'SMS', 'INBOUND', v_base + interval '9 days 4 min',
 'Whiskers is NOT impressed haha. But Luna is the sweetest dog ever. Marcus is already wrapped around her paw. We take her running in Cheesman Park every morning', now(), now()),
(v_user_id, v_conv_sarah, NULL, 'SMS', 'OUTBOUND', v_base + interval '9 days 6 min',
 'That''s adorable. Hey what are you doing this weekend? Want to grab brunch?', now(), now()),
(v_user_id, v_conv_sarah, v_sarah_id, 'SMS', 'INBOUND', v_base + interval '9 days 8 min',
 'Yes! How about Snooze on Sunday? Their pineapple upside down pancakes are legendary. 10am?', now(), now()),
(v_user_id, v_conv_sarah, NULL, 'SMS', 'OUTBOUND', v_base + interval '9 days 10 min',
 'Done! See you there. Tell Marcus and Luna I said hi!', now(), now());

RAISE NOTICE 'Demo search data inserted: 4 contacts, 4 conversations, 78 messages';
END $$;
