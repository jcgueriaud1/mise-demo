Mise — AI-integrated weekly meal planner demo
Copyright 2026 the Mise demo authors

Licensed under the Apache License, Version 2.0 (see LICENSE.md).

This product includes material from the following third-party projects:

- **Tabler Icons** — https://tabler.io/icons
  Copyright (c) 2020–2024 Paweł Kuna, MIT License
  (https://github.com/tabler/tabler-icons/blob/main/LICENSE)
  The Mise logo is the [`tools-kitchen-2`](https://tabler.io/icons/icon/tools-kitchen-2)
  outline icon, inlined in `MainLayout` and copied at
  `src/main/resources/META-INF/resources/icons/tools-kitchen-2.svg`.
  The design mockups under `ai-meal-planner/mise/` also use Tabler outline icons.

- **Unified Testing Tool** — https://github.com/parttio/unified-testing-tool
  Apache License 2.0. Used as a test dependency (`org.vaadin.addons:unified-test-*`);
  its migration recipe generated the `write-unified-ui-tests` skill under `.claude/skills/`.

- **DramaFinder** — https://github.com/parttio/dramafinder
  Copyright the DramaFinder contributors, Apache License 2.0.
  Used as a test dependency (transitively, through the Unified API's Playwright adapter),
  and parts of its documentation are vendored into the `vaadin-playwright-test` skill
  under `.claude/skills/`.
