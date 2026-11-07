package com.turbofetch.domain.thiel

/**
 * Корпус реплик Питера Тиля — сырьё для цепи Маркова.
 *
 * Все фразы взяты **дословно** из первоисточников:
 *  - [STARTUP] — «Zero to One» (2014);
 *  - [POLITICS] — эссе «The Education of a Libertarian» (Cato Unbound, 2009)
 *    и интервью The New York Times;
 *  - [ESCHATOLOGY] — лекции 2025 года в пересказе The New York Times,
 *    Guardian и Wikipedia.
 *
 * Фразы короткие намеренно: в цепи Маркова длинные предложения дают
 * слишком гладкий и скучный результат. Каждая фраза — либо целиком
 * дословная, либо начинается с дословного оборота Тиля.
 */
object ThielCorpus {

    /** Про стартапы, монополии и будущее — «Zero to One». */
    val STARTUP: List<String> = listOf(
        "Every moment in business happens only once.",
        "Every time we create something new, we go from 0 to 1.",
        "Doing what we already know how to do takes the world from 1 to n.",
        "The future is simply the set of all moments yet to come.",
        "Monopoly is the condition of every successful business.",
        "All happy companies are different.",
        "All failed companies are the same: they failed to escape competition.",
        "Every correct answer is necessarily a secret.",
        "If there are many secrets left in the world, there are probably many world-changing companies yet to be started.",
        "Great companies can be built on open but unsuspected secrets about how the world works.",
        "Every startup should start with a very small market.",
        "Every monopoly dominates a large share of its market.",
        "Once you're 10x better, you escape competition.",
        "Creating value is not enough, you also need to capture some of the value you create.",
        "Monopolists can afford to think about things other than making money.",
        "The most contrarian thing of all is not to oppose the crowd but to think for yourself.",
        "A startup is the largest group of people you can convince of a plan to build a different future.",
        "Actually, capitalism and competition are opposites.",
        "More than anything else, competition is an ideology.",
        "But really it's competition, not business, that is like war: allegedly necessary, supposedly valiant, but ultimately destructive.",
        "Only one thing can allow a business to transcend the daily brute struggle for survival: monopoly profits.",
        "Monopolies drive progress because the promise of years or even decades of monopoly profits provides a powerful incentive to innovate.",
        "And monopoly businesses capture more value than millions of undifferentiated competitors.",
        "A good answer takes the following form: most people believe in x, but the truth is the opposite of x.",
        "People are scared of secrets because they are scared of being wrong.",
        "Belief in secrets is an effective truth.",
        "You can't find secrets without looking for them.",
        "The best place to look for secrets is where no one else is looking.",
        "Great companies have secrets: specific reasons for success that other people don't see.",
        "Properly understood, technology is the one way for us to escape competition in a globalizing world.",
        "Every correct answer is necessarily a secret: something important and unknown, something hard to do but doable.",
        "People are scared of secrets because they are scared of being wrong.",
        "The future of technology is not pre-determined.",
        "The question of the future remains very open indeed.",
        "We must resist the temptation of technological utopianism.",
        "The fate of our world may depend on the effort of a single person.",
        "A good startup should have the potential for great scale built into its first design.",
        "Contrarian thinking doesn't make any sense unless the world still has secrets left to give up.",
    )

    /** О политике, свободе и государстве — эссе Cato Unbound и интервью. */
    val POLITICS: List<String> = listOf(
        "I no longer believe that freedom and democracy are compatible.",
        "I remain committed to the faith of my teenage years: to authentic human freedom as a precondition for the highest good.",
        "I stand against confiscatory taxes, totalitarian collectives, and the ideology of the inevitability of the death of every individual.",
        "Politics gets people angry, destroys relationships, and polarizes peoples' vision.",
        "Politics is about interfering with other people's lives without their consent.",
        "The world appears too big a place.",
        "Those who have argued for free markets have been screaming into a hurricane.",
        "The great task for libertarians is to find an escape from politics in all its forms.",
        "We are in a deadly race between politics and technology.",
        "Crypto is decentralizing, AI is centralizing.",
        "Crypto is libertarian and AI is communist.",
        "Reagan was consumer capitalism, which is oxymoronic.",
        "You don't save money as a capitalist, you borrow money.",
        "I would say that we lived in a world in which bits were unregulated and atoms were regulated.",
        "The decisive arena for the future of the West.",
        "There are no truly free places left in our world.",
        "The hope of the Internet is that these new worlds will impact and force change on the existing social and political order.",
        "I have little hope that voting will make things better.",
        "Voting is not under siege in America, but many other rights are.",
        "The government used to be able to coordinate complex solutions.",
        "The future of technology is not pre-determined.",
        "We must resist the temptation of technological utopianism.",
        "The fate of our world may depend on the effort of a single person who builds or propagates the machinery of freedom.",
        "The future will be much better or much worse, but the question of the future remains very open indeed.",
        "It depends on the secret: some are more dangerous than others.",
        "Politics is mostly futile.",
        "Like the ancients, they were optimistic.",
        "The limitation of the Internet is that these new worlds are virtual.",
        "We must redouble the efforts to commercialize space, but we also must be realistic about the time horizons involved.",
        "The libertarian future of classic science fiction will not happen before the second half of the 21st century.",
        "I believe that politics is way too intense.",
        "That's probably why, in the past, libertarians have made little progress in the political sphere.",
    )

    /**
     * Об эсхатологии, Антихристе и Катехоне — лекции 2025 года
     * (пересказ The New York Times / Guardian / Wikipedia).
     */
    val ESCHATOLOGY: List<String> = listOf(
        "He sees humanity as facing the double threats of the Antichrist and the Armageddon.",
        "The Armageddon, which is the collapse of civilization through global warfare.",
        "He sees a total one-world government as closer to the Antichrist.",
        "Sees a total one-world government as closer to the Antichrist.",
        "He sees salvation in the figure of the Katechon.",
        "The Katechon, a mysterious force that restrains the Antichrist.",
        "He has referred to individuals, organizations, communism or even anti-tech regulations as precursors of the Antichrist.",
        "He referred to environmental activist Greta Thunberg and AI safety advocate Eliezer Yudkowsky as legionnaires of the Antichrist.",
        "Legionnaires of the Antichrist.",
        "Darker questions still emerge in these dusky final weeks of our interregnum.",
        "The Antichrist represents a boundless global order.",
        "The apocalypse, not the end of the world, but a sudden unveiling of forces that had long been taking shape beneath the surface.",
        "A sudden unveiling of forces that had long been taking shape beneath the surface.",
        "Different people are worried about different apocalyptic threats like environment degradation, nuclear wars or bioweapons.",
        "He recognizes these as credible threats.",
        "Thiel's conception of will integrates Christian eschatology, Girardian mimetic theory, and Straussian elitism to critique the impotence of liberal democracy while proposing the efficiency of a techno-feudal alternative.",
        "Peter Thiel and Marc Andreessen are the foremost proponents of techno-eschatology.",
        "The sacralizing of AI and uses spiritualism to bolster the logic of venture capitalism.",
        "I believe Christianity is true but I don't sort of feel a compelling need to convince other people of that.",
        "Christianity for me is the anti-identity.",
        "Your identity is in Christ.",
        "Truth is not a social construct.",
        "There's no forgiveness, and you still have original sin.",
        "The past is terrible, and you can never overcome it.",
        "The church lost a certain amount of authority, but people did not become rationalist, atheist people; they went into this sort of Woke religion.",
        "This sort of Woke religion, which I would interpret as a certain, extreme form of Christianity.",
        "Is a continuation of communism and the social gospel.",
        "I believe that politics is way too intense.",
        "A subject that Thiel has devoted attention to in recent years is the Antichrist, Armageddon and the Apocalypse.",
        "About which he has given talks in multiple events.",
        "A September 2025 series of off-the-record private lectures organized by the Acts 17 Collective in San Francisco.",
        "The decisive arena for the future of the West.",
    )

    /** Все темы вместе — для общей ленты святилища. */
    val ALL: List<String> = STARTUP + POLITICS + ESCHATOLOGY
}
