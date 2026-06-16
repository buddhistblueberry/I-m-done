import { useState } from "react";
import { Link, useLocation } from "wouter";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Search, Menu, X } from "lucide-react";
import { useAuth } from "@/_core/hooks/useAuth";
import { getLoginUrl } from "@/const";

export function Header() {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [, navigate] = useLocation();
  const { user, logout } = useAuth();

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/search?q=${encodeURIComponent(searchQuery)}`);
      setSearchQuery("");
    }
  };

  return (
    <header className="sticky top-0 z-40 bg-gray-900 border-b-2 border-cyan-400">
      <div className="container mx-auto px-4 py-4">
        <div className="flex items-center justify-between gap-4">
          {/* Logo */}
          <Link href="/">
            <div className="flex items-center gap-2 cursor-pointer group">
              <div className="text-2xl font-cyberpunk text-pink-500 group-hover:text-cyan-400 transition-colors">
                I-m-done
              </div>
            </div>
          </Link>

          {/* Desktop Navigation */}
          <div className="hidden md:flex items-center gap-6">
            <Link href="/">
              <span className="text-sm font-cyberpunk text-gray-300 hover:text-cyan-400 transition-colors cursor-pointer">
                Home
              </span>
            </Link>
            <Link href="/search">
              <span className="text-sm font-cyberpunk text-gray-300 hover:text-cyan-400 transition-colors cursor-pointer">
                Search
              </span>
            </Link>
          </div>

          {/* Search Bar */}
          <form onSubmit={handleSearch} className="hidden sm:flex flex-1 max-w-xs">
            <div className="relative w-full">
              <Search className="absolute left-3 top-2.5 w-4 h-4 text-cyan-400" />
              <Input
                type="text"
                placeholder="Search..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-10 py-2 bg-gray-800 border-2 border-cyan-400 text-white placeholder-gray-500 rounded-sm focus:border-pink-500 transition-colors text-sm"
              />
            </div>
          </form>

          {/* Auth Buttons */}
          <div className="hidden md:flex items-center gap-3">
            {user ? (
              <>
                <span className="text-sm text-gray-300">{user.name}</span>
                <Button
                  size="sm"
                  variant="outline"
                  className="btn-neon text-xs"
                  onClick={() => logout()}
                >
                  Logout
                </Button>
              </>
            ) : (
              <Button
                size="sm"
                className="btn-neon-cyan text-xs"
                onClick={() => (window.location.href = getLoginUrl())}
              >
                Login
              </Button>
            )}
          </div>

          {/* Mobile Menu Button */}
          <button
            onClick={() => setIsMenuOpen(!isMenuOpen)}
            className="md:hidden p-2 hover:bg-gray-800 rounded-sm transition-colors"
          >
            {isMenuOpen ? (
              <X className="w-5 h-5 text-cyan-400" />
            ) : (
              <Menu className="w-5 h-5 text-cyan-400" />
            )}
          </button>
        </div>

        {/* Mobile Menu */}
        {isMenuOpen && (
          <div className="md:hidden mt-4 space-y-3 border-t-2 border-cyan-400 pt-4">
            <Link href="/">
              <span className="block text-sm font-cyberpunk text-gray-300 hover:text-cyan-400 transition-colors cursor-pointer">
                Home
              </span>
            </Link>
            <Link href="/search">
              <span className="block text-sm font-cyberpunk text-gray-300 hover:text-cyan-400 transition-colors cursor-pointer">
                Search
              </span>
            </Link>

            {/* Mobile Search */}
            <form onSubmit={handleSearch} className="sm:hidden">
              <div className="relative">
                <Search className="absolute left-3 top-2.5 w-4 h-4 text-cyan-400" />
                <Input
                  type="text"
                  placeholder="Search..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-10 py-2 w-full bg-gray-800 border-2 border-cyan-400 text-white placeholder-gray-500 rounded-sm focus:border-pink-500 transition-colors text-sm"
                />
              </div>
            </form>

            {/* Mobile Auth */}
            <div className="pt-2 border-t-2 border-cyan-400">
              {user ? (
                <>
                  <p className="text-sm text-gray-300 mb-2">{user.name}</p>
                  <Button
                    size="sm"
                    variant="outline"
                    className="btn-neon w-full text-xs"
                    onClick={() => logout()}
                  >
                    Logout
                  </Button>
                </>
              ) : (
                <Button
                  size="sm"
                  className="btn-neon-cyan w-full text-xs"
                  onClick={() => (window.location.href = getLoginUrl())}
                >
                  Login
                </Button>
              )}
            </div>
          </div>
        )}
      </div>
    </header>
  );
}
