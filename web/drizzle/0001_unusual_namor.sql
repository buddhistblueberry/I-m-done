CREATE TABLE `favorites` (
	`id` int AUTO_INCREMENT NOT NULL,
	`userId` int NOT NULL,
	`tmdbId` int NOT NULL,
	`mediaType` enum('movie','tv') NOT NULL,
	`title` text,
	`posterPath` text,
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	CONSTRAINT `favorites_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `watchHistory` (
	`id` int AUTO_INCREMENT NOT NULL,
	`userId` int NOT NULL,
	`tmdbId` int NOT NULL,
	`mediaType` enum('movie','tv') NOT NULL,
	`title` text,
	`posterPath` text,
	`season` int,
	`episode` int,
	`watchedAt` timestamp NOT NULL DEFAULT (now()),
	`progressSeconds` int DEFAULT 0,
	`totalSeconds` int,
	CONSTRAINT `watchHistory_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `watchlist` (
	`id` int AUTO_INCREMENT NOT NULL,
	`userId` int NOT NULL,
	`tmdbId` int NOT NULL,
	`mediaType` enum('movie','tv') NOT NULL,
	`title` text,
	`posterPath` text,
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	CONSTRAINT `watchlist_id` PRIMARY KEY(`id`)
);
