# Go Do It

This is a project management tool that was built almost entirely with Google AntiGravity's AI agent. It is designed to use HTMX and Thymeleaf for its simplicity on the front-end. 

## But Y Tho?

While it does serve a functional purpose (I need such a tool for my own projects, but don't want to use any "free" offerings or sign up for anything else), it is mostly meant as an experiment to see just how far an AI agent can make it on its own with minimal code intervention from a human.

I've tried to limit my manual code changes to extremely basic tasks. Changing some copy here and there. Shifting something a pixel or two. Deleting extraneous startup sql. Writing a README...

## Where is this going?

The initial commit represents a minimum viable product that was reached using Gemini 3 Pro as the model. You wouldn't want to use it at your company, but as a slightly more advanced "To-Do" app it gets the job done. Everything after that, unless otherwise noted, is AntiGravity iterating on it. Sometimes, even consulting the app in the browser itself to find tickets to work on (Inception sound here)

That said, it isn't rocket science to whip something like this up yourself, it's just tedious. What will be really interesting is seeing how well AntiGravity does with the more complex features. Next steps are, in no particular order

* Add a "Projects" feature that actually scopes the tasks to a project. Currently you can create projects, but only the first one actually has Sprints or Tasks associated with it.
* ~~Make Statuses truly customizable. While they are driven from the database they are currently not changeable, and certainly not scoped to a project.~~ Done, it had a way easier time of it than I expected.
* Provide the ability to change which project you are viewing. Again, everything is sort of global scope at the moment.
* Security - There isn't any at the moment. I want to see it provide OAuth with a common provider like Google to start with, then maybe we can look at maintaining separate credentials with either email verification or 2FA (probably the former, don't really want to spend money on this)
* Making this easily deployable - Again, not trying to host the next Jira here. I just want to see how far this goes and, if it shakes out, provide an open source, free tool people can easily use.
* Make it mobile friendly - probably just good policy
* Make it accessible - again, just good policy
* Provide a non-dark mode - grumble grumble we'll see
* Test automation - interested to see how well the AI does creating meaningful tests for code it wrote itself. Meaningful being the operative word
* Sonar coverage and metric tracking - right now my criteria for determining how AntiGravity works vs. something like CoPilot or a junior developer is basically "it kind of looks better or worse", which is hardly a shining example of scientific rigor. I want to get some actual numbers
* Support comments - basic functionality
* Support subtasks - basic functionality
