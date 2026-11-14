package app.easywake;

/** Easy dinners. Each recipe: name, time and servings, ingredients, steps. */
final class Recipes {
    private Recipes() {}

    static final String[] NAMES = {
            "One-pan lemon chicken and potatoes",
            "Ground beef tacos",
            "Spaghetti with meat sauce",
            "Sheet-pan sausage and peppers",
            "Chicken stir-fry",
            "Baked salmon with rice and broccoli",
            "Easy chili",
            "Chicken quesadillas",
            "Garlic butter shrimp pasta",
            "Oven BBQ chicken thighs",
            "Fried rice",
            "Stovetop smash burgers",
            "Tomato soup and grilled cheese",
            "Loaded baked potatoes",
    };

    static final String[] INFO = {
            "50 minutes, mostly hands-off  •  serves 4",
            "20 minutes  •  serves 4",
            "35 minutes  •  serves 4 to 6",
            "35 minutes  •  serves 4",
            "25 minutes  •  serves 4",
            "25 minutes  •  serves 2",
            "45 minutes  •  serves 6",
            "15 minutes  •  serves 2",
            "20 minutes  •  serves 3 to 4",
            "45 minutes, mostly hands-off  •  serves 4",
            "20 minutes  •  serves 3 to 4",
            "15 minutes  •  serves 4",
            "25 minutes  •  serves 3 to 4",
            "1 hour, mostly hands-off  •  serves 4",
    };

    static final String[][] INGREDIENTS = {
            {"8 bone-in chicken thighs or drumsticks", "1.5 lb small potatoes, halved",
                    "3 tbsp olive oil", "1 lemon", "4 garlic cloves, smashed",
                    "1 tsp dried oregano or thyme", "Salt and pepper"},
            {"1 lb ground beef", "1 packet taco seasoning (or 2 tbsp chili powder, 1 tsp "
                    + "cumin, 1 tsp salt)", "8 taco shells or small tortillas",
                    "Shredded lettuce, cheese, diced tomato", "Salsa or sour cream"},
            {"1 lb ground beef", "1 onion, chopped", "2 garlic cloves, minced",
                    "1 jar (24 oz) pasta sauce", "1 lb spaghetti", "Salt",
                    "Grated parmesan"},
            {"1 lb smoked or Italian sausage, sliced", "3 bell peppers, sliced",
                    "1 onion, sliced", "2 tbsp olive oil", "1 tsp Italian seasoning",
                    "Salt and pepper", "Rolls or rice, to serve"},
            {"1 lb chicken breast, sliced thin", "4 cups mixed vegetables (broccoli, "
                    + "peppers, carrots, snap peas)", "2 tbsp oil", "3 tbsp soy sauce",
                    "1 tbsp honey or brown sugar", "2 garlic cloves, minced",
                    "1 tsp cornstarch mixed with 2 tbsp water", "Cooked rice"},
            {"2 salmon fillets", "1 tbsp olive oil", "Salt, pepper, lemon",
                    "2 cups broccoli florets", "1 cup rice"},
            {"1 lb ground beef", "1 onion, chopped", "2 cans (15 oz) beans, drained",
                    "1 can (28 oz) crushed tomatoes", "2 tbsp chili powder", "1 tsp cumin",
                    "1 tsp salt", "Cheese, sour cream or crackers to serve"},
            {"2 cups cooked chicken, shredded (rotisserie works)", "4 large flour tortillas",
                    "2 cups shredded cheese", "Salsa", "A little oil or butter"},
            {"12 oz spaghetti or linguine", "1 lb peeled shrimp", "4 tbsp butter",
                    "4 garlic cloves, minced", "Juice of half a lemon",
                    "Pinch of red pepper flakes", "Salt, pepper, parsley"},
            {"8 bone-in chicken thighs", "1 cup barbecue sauce", "1 tbsp oil",
                    "Salt and pepper"},
            {"3 cups cold cooked rice (day-old is best)", "2 eggs",
                    "1 cup frozen peas and carrots", "2 green onions, sliced",
                    "2 tbsp soy sauce", "2 tbsp oil", "Leftover chicken, ham or shrimp "
                    + "(optional)"},
            {"1 lb ground beef (80/20)", "4 burger buns", "4 slices cheese",
                    "Salt and pepper", "Pickles, onion, ketchup, mustard"},
            {"1 can (28 oz) crushed tomatoes", "1 onion, chopped", "2 tbsp butter",
                    "2 cups broth", "Half cup cream or milk (optional)", "8 slices bread",
                    "4 to 8 slices cheese", "Butter for the bread"},
            {"4 large russet potatoes", "Oil and salt", "Butter", "Shredded cheese",
                    "Sour cream", "Cooked bacon bits or leftover chili", "Green onions"},
    };

    static final String[][] STEPS = {
            {"Heat the oven to 425°F.",
                    "Toss the potatoes and chicken on a large baking sheet with the oil, "
                            + "garlic, herbs, the juice of half the lemon, and plenty of "
                            + "salt and pepper.",
                    "Arrange the chicken skin side up with the potatoes cut side down. "
                            + "Slice the other lemon half and tuck the slices in.",
                    "Roast 40 to 45 minutes, until the skin is browned and the chicken "
                            + "reaches 165°F inside."},
            {"Brown the beef in a skillet over medium-high heat, breaking it up, about 7 "
                    + "minutes. Drain off extra fat.",
                    "Stir in the seasoning and half a cup of water. Simmer 3 to 4 minutes "
                            + "until thick.",
                    "Warm the shells or tortillas, fill, and add toppings."},
            {"Put a large pot of salted water on to boil.",
                    "In a deep skillet, brown the beef with the onion over medium-high "
                            + "heat, about 8 minutes. Add the garlic for the last minute. "
                            + "Drain extra fat.",
                    "Pour in the sauce, lower the heat, and simmer while the pasta cooks.",
                    "Cook the spaghetti as the package says. Drain, toss with the sauce, "
                            + "and top with parmesan."},
            {"Heat the oven to 425°F.",
                    "Toss everything on a baking sheet with the oil, seasoning, salt and "
                            + "pepper. Spread it out in one layer.",
                    "Roast 25 minutes, stirring once, until the peppers are soft and the "
                            + "sausage is browned.",
                    "Serve in rolls or over rice."},
            {"Mix the soy sauce, honey and garlic in a cup.",
                    "Heat 1 tablespoon of oil in a large skillet over high heat. Cook the "
                            + "chicken 4 to 5 minutes until no pink remains. Take it out.",
                    "Add the rest of the oil and the vegetables. Stir 4 to 5 minutes until "
                            + "crisp-tender.",
                    "Return the chicken, pour in the sauce and the cornstarch mix, and "
                            + "stir 1 minute until glossy. Serve over rice."},
            {"Start the rice as the package says. Heat the oven to 400°F.",
                    "Put the salmon and broccoli on a baking sheet. Drizzle with oil, "
                            + "season with salt and pepper.",
                    "Bake 12 to 15 minutes, until the salmon flakes easily with a fork "
                            + "(145°F inside).",
                    "Squeeze lemon over everything and serve with the rice."},
            {"In a large pot, brown the beef with the onion over medium-high heat, about 8 "
                    + "minutes. Drain extra fat.",
                    "Stir in the chili powder, cumin and salt for 30 seconds.",
                    "Add the tomatoes and beans plus one cup of water. Bring to a boil, "
                            + "then simmer 30 minutes, stirring now and then.",
                    "Taste, add salt if needed, and serve with toppings."},
            {"Sprinkle cheese over half of each tortilla, add chicken, a spoon of salsa, "
                    + "and a little more cheese. Fold in half.",
                    "Heat a skillet over medium with a little oil or butter.",
                    "Cook each quesadilla 2 to 3 minutes a side until golden and the "
                            + "cheese has melted. Cut into wedges."},
            {"Boil the pasta in salted water as the package says. Save half a cup of the "
                    + "water before draining.",
                    "While it cooks, melt the butter in a large skillet over medium heat. "
                            + "Add the garlic and pepper flakes for 30 seconds.",
                    "Add the shrimp in one layer. Cook about 2 minutes a side until pink "
                            + "and curled.",
                    "Toss in the pasta, lemon juice and a splash of the pasta water. "
                            + "Season and scatter with parsley."},
            {"Heat the oven to 400°F and line a baking sheet with foil.",
                    "Rub the chicken with oil, salt and pepper. Bake skin side up for 30 "
                            + "minutes.",
                    "Brush generously with barbecue sauce and bake 10 to 15 minutes more, "
                            + "until sticky and 165°F inside."},
            {"Heat 1 tablespoon of oil in a large skillet over medium-high. Scramble the "
                    + "eggs, then set them aside.",
                    "Add the rest of the oil and the peas and carrots. Cook 2 minutes.",
                    "Add the rice, breaking up clumps, and let it fry 3 to 4 minutes "
                            + "without stirring too much so it gets a little crisp.",
                    "Stir in the soy sauce, eggs, green onions and any leftover meat. Heat "
                            + "through."},
            {"Divide the beef into 4 loose balls. Don't pack them.",
                    "Heat a heavy skillet over high heat until very hot. Add the balls and "
                            + "press each flat with a spatula. Season with salt and pepper.",
                    "Cook 2 to 3 minutes until the edges are brown and crisp. Flip, add "
                            + "cheese, and cook 1 to 2 minutes more (160°F inside).",
                    "Serve on buns with your toppings."},
            {"Melt the butter in a pot over medium heat. Cook the onion 5 minutes until "
                    + "soft.",
                    "Add the tomatoes and broth. Simmer 15 minutes. Blend if you like it "
                            + "smooth, then stir in the cream and season.",
                    "Butter one side of each slice of bread. Put cheese between two slices "
                            + "with the butter facing out.",
                    "Cook the sandwiches in a skillet over medium heat, 3 minutes a side, "
                            + "until golden and melted."},
            {"Heat the oven to 425°F. Scrub the potatoes, poke each a few times with a "
                    + "fork, and rub with oil and salt.",
                    "Bake directly on the rack for 50 to 60 minutes, until a knife slides "
                            + "in easily.",
                    "Split them open, fluff the insides with a fork, and add butter and "
                            + "salt.",
                    "Pile on cheese, sour cream, bacon or chili, and green onions."},
    };
}
